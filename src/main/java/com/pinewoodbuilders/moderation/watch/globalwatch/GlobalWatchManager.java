package com.pinewoodbuilders.moderation.watch.globalwatch;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.moderation.watch.WatchContainer;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogType;
import com.pinewoodbuilders.time.Carbon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class GlobalWatchManager {
    private final Xeus avaire;

    /**
     * Creates the mute manager instance with the given Xeus application instance,
     * the mute manager will sync the mutes entities from the database into memory.
     *
     * @param avaire The main Xeus instance.
     */
    public GlobalWatchManager(Xeus avaire) {
        this.avaire = avaire;

        syncWithGlobalDatabase();
    }

    private final HashMap <Long, HashSet <WatchContainer>> globalWatches = new HashMap<>();

    public void registerGlobalWatch(String caseId, long mainGroupId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!globalWatches.containsKey(mainGroupId)) {
            globalWatches.put(mainGroupId, new HashSet<>());
        }

        if (isGlobalWatched(mainGroupId, userId)) {
            unregisterGlobalWatch(mainGroupId, userId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.ON_WATCH_TABLE_NAME).insert(statement -> {
            statement.set("guild_id", 0);
            statement.set("modlog_id", caseId);
            statement.set("expires_in", expiresAt);
            statement.set("global", true);
            statement.set("main_group_id", mainGroupId);
        });

        globalWatches.get(mainGroupId).add(new WatchContainer(0, userId, mainGroupId, expiresAt, true));
    }

    /**
     * Unregisters a mute matching the given guild ID and user ID.
     *
     * @param userId  The ID of the user that should be unmuted.
     * @throws SQLException If the unmute fails to delete the mute record from the
     *                      database.
     */
    public void unregisterGlobalWatch(long mainGroupId, long userId) throws SQLException {
        if (!globalWatches.containsKey(mainGroupId)) {
            return;
        }

        final boolean[] removedEntities = { false };
        synchronized (globalWatches) {
            globalWatches.get(mainGroupId).removeIf(next -> {
                if (!next.isGlobalSame(mainGroupId, userId)) {
                    return false;
                }

                if (next.getSchedule() != null) {
                    next.cancelSchedule();
                }

                removedEntities[0] = true;
                return true;
            });
        }

        if (removedEntities[0]) {
            cleanupGlobalWatches(mainGroupId, userId);
        }
    }

    public boolean isGlobalWatched(long mainGroupId, long userId) {
        if (!globalWatches.containsKey(mainGroupId)) {
            return false;
        }

        for (WatchContainer container : globalWatches.get(mainGroupId)) {
            if (container.isGlobalSame(userId, mainGroupId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the total amount of mutes currently stored in memory, this includes
     * permanent and temporary mutes.
     *
     * @return The total amount of mutes stored.
     */
    public int getTotalAmountOfGlobalWatches() {
        int totalWatches = 0;
        for (Map.Entry<Long, HashSet<WatchContainer>> entry : globalWatches.entrySet()) {
            totalWatches += entry.getValue().size();
        }
        return totalWatches;
    }

    public HashMap<Long, HashSet<WatchContainer>> getGlobalWatches() {
        return globalWatches;
    }

    private final Logger log = LoggerFactory.getLogger(GlobalWatchManager.class);
    private void syncWithGlobalDatabase() {
        log.info("Syncing global watches with the database...");

        String query = I18n.format(
            "SELECT `{1}`.`mgi`, `{1}`.`target_id`, `{0}`.`expires_in` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`main_group_id` = `{1}`.`mgi` AND `{0}`.`global` = 1;",
            Constants.ON_WATCH_TABLE_NAME, Constants.MGM_LOG_TABLE_NAME);

        System.out.println(query);
        try {
            int size = getTotalAmountOfWatches();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long mgi = row.getLong("mgi");

                if (!globalWatches.containsKey(mgi)) {
                    globalWatches.put(mgi, new HashSet<>());
                }


                globalWatches.get(mgi).add(new WatchContainer(0, row.getLong("target_id"), row.getLong("mgi"),
                    row.getTimestamp("expires_in"), row.getBoolean("global")));
            }

            log.info("Syncing complete! {} global watch entries was found that has not expired yet",
                getTotalAmountOfWatches() - size);
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }
    private void cleanupGlobalWatches(long mainGroupId, long userId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.ON_WATCH_TABLE_NAME)
            .select(Constants.ON_WATCH_TABLE_NAME + ".modlog_id as id")
            .innerJoin(Constants.MGM_LOG_TABLE_NAME, Constants.ON_WATCH_TABLE_NAME + ".modlog_id",
                Constants.MGM_LOG_TABLE_NAME + ".modlogCase")
            .where(Constants.MGM_LOG_TABLE_NAME + ".mgi", mainGroupId)
            .andWhere(Constants.MGM_LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.ON_WATCH_TABLE_NAME + ".main_group_id", mainGroupId)
            .andWhere(builder -> builder.where(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_WATCH.getId())
                .orWhere(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_TEMP_WATCH.getId()))
            .get();


        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `guild_id` = ? AND `main_group_id` = ? AND `modlog_id` = ?",
                Constants.ON_WATCH_TABLE_NAME);

            avaire.getDatabase().queryBatch(query, statement -> {
                for (DataRow row : collection) {
                    statement.setLong(1, 0);
                    statement.setLong(2, mainGroupId);
                    statement.setString(3, row.getString("id"));
                    statement.addBatch();
                }
            });
        }
    }


    /**
     * Gets the total amount of mutes currently stored in memory, this includes
     * permanent and temporary mutes.
     *
     * @return The total amount of mutes stored.
     */
    public int getTotalAmountOfWatches() {
        int totalWatches = 0;
        for (Map.Entry<Long, HashSet<WatchContainer>> entry : globalWatches.entrySet()) {
            totalWatches += entry.getValue().size();
        }
        return totalWatches;
    }

}
