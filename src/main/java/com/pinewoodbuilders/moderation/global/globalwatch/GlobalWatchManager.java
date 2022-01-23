package com.pinewoodbuilders.moderation.global.globalwatch;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.language.I18n;
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

    private final HashMap <Long, HashSet <GlobalWatchContainer>> globalWatches = new HashMap<>();

    public void registerGlobalWatch(long ranGuildId, String caseId, long mainGroupId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!globalWatches.containsKey(mainGroupId)) {
            globalWatches.put(mainGroupId, new HashSet<>());
        }

        if (isGlobalWatched(mainGroupId, userId, ranGuildId)) {
            unregisterGlobalWatch(mainGroupId, userId, ranGuildId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_WATCH_TABLE_NAME).insert(statement -> {
            statement.set("guild_id", ranGuildId);
            statement.set("modlog_id", caseId);
            statement.set("expires_in", expiresAt);
            statement.set("main_group_id", mainGroupId);
        });

        globalWatches.get(mainGroupId).add(new GlobalWatchContainer(ranGuildId, userId, expiresAt, mainGroupId));
    }

    /**
     * Unregisters a mute matching the given guild ID and user ID.
     *
     * @param userId  The ID of the user that should be unmuted.
     * @throws SQLException If the unmute fails to delete the mute record from the
     *                      database.
     */
    public void unregisterGlobalWatch(long mainGroupId, long userId, long ranGuildId) throws SQLException {
        if (!globalWatches.containsKey(mainGroupId)) {
            return;
        }

        final boolean[] removedEntities = { false };
        synchronized (globalWatches) {
            globalWatches.get(mainGroupId).removeIf(next -> {
                if (!next.isSame(ranGuildId, userId, mainGroupId)) {
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
            cleanupGlobalWatches(mainGroupId, userId, ranGuildId);
        }
    }

    public boolean isGlobalWatched(long mainGroupId, long userId, long ranGuildId) {
        if (!globalWatches.containsKey(mainGroupId)) {
            return false;
        }

        for (GlobalWatchContainer container : globalWatches.get(mainGroupId)) {
            if (container.isSame(ranGuildId, userId, mainGroupId)) {
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
        for (Map.Entry<Long, HashSet<GlobalWatchContainer>> entry : globalWatches.entrySet()) {
            totalWatches += entry.getValue().size();
        }
        return totalWatches;
    }

    public HashMap<Long, HashSet<GlobalWatchContainer>> getGlobalWatches() {
        return globalWatches;
    }

    private final Logger log = LoggerFactory.getLogger(GlobalWatchManager.class);
    private void syncWithGlobalDatabase() {
        log.info("Syncing global watches with the database...");

        String query = I18n.format(
            "SELECT `{1}`.`mgi`, `{1}`.`target_id`, `{0}`.`guild_id`, `{0}`.`expires_in` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`main_group_id` = `{1}`.`mgi`",
            Constants.GLOBAL_WATCH_TABLE_NAME, Constants.MGM_LOG_TABLE_NAME);

        System.out.println(query);
        try {
            int size = getTotalAmountOfWatches();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long mgi = row.getLong("mgi");

                if (!globalWatches.containsKey(mgi)) {
                    globalWatches.put(mgi, new HashSet<>());
                }


                globalWatches.get(mgi).add(new GlobalWatchContainer(
                    row.getLong("guild_id"),
                    row.getLong("target_id"),
                    row.getTimestamp("expires_in"),
                    row.getLong("mgi")
                ));
            }

            log.info("Syncing complete! {} global watch entries was found that has not expired yet",
                getTotalAmountOfWatches() - size);
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }
    private void cleanupGlobalWatches(long mainGroupId, long userId, long ranGuildId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_WATCH_TABLE_NAME)
            .select(Constants.GLOBAL_WATCH_TABLE_NAME + ".modlog_id as id")
            .innerJoin(Constants.MGM_LOG_TABLE_NAME, Constants.GLOBAL_WATCH_TABLE_NAME + ".modlog_id",
                Constants.MGM_LOG_TABLE_NAME + ".modlogCase")
            .where(Constants.MGM_LOG_TABLE_NAME + ".mgi", mainGroupId)
            .andWhere(Constants.MGM_LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.GLOBAL_WATCH_TABLE_NAME + ".main_group_id", mainGroupId)
            .andWhere(Constants.GLOBAL_WATCH_TABLE_NAME + ".guild_id", ranGuildId)
            .andWhere(builder -> builder.where(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_WATCH.getId())
                .orWhere(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_TEMP_WATCH.getId()))
            .get();


        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `guild_id` = ? AND `main_group_id` = ? AND `modlog_id` = ?",
                Constants.GLOBAL_WATCH_TABLE_NAME);

            avaire.getDatabase().queryBatch(query, statement -> {
                for (DataRow row : collection) {
                    statement.setLong(1, ranGuildId);
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
        for (Map.Entry<Long, HashSet<GlobalWatchContainer>> entry : globalWatches.entrySet()) {
            totalWatches += entry.getValue().size();
        }
        return totalWatches;
    }

}
