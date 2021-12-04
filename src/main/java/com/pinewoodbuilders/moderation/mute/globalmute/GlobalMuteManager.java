package com.pinewoodbuilders.moderation.mute.globalmute;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.moderation.mute.MuteContainer;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogType;
import com.pinewoodbuilders.time.Carbon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class GlobalMuteManager {
    private final Xeus avaire;

    /**
     * Creates the mute manager instance with the given Xeus application instance,
     * the mute manager will sync the mutes entities from the database into memory.
     *
     * @param avaire The main Xeus instance.
     */
    public GlobalMuteManager(Xeus avaire) {
        this.avaire = avaire;

        syncWithGlobalDatabase();
    }

    private final HashMap <Long, HashSet <MuteContainer>> globalMutes = new HashMap<>();

    public void registerGlobalMute(String caseId, long mainGroupId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!globalMutes.containsKey(mainGroupId)) {
            globalMutes.put(mainGroupId, new HashSet<>());
        }

        if (isGlobalMuted(mainGroupId, userId)) {
            unregisterGlobalMute(mainGroupId, userId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.MUTE_TABLE_NAME).insert(statement -> {
            statement.set("guild_id", 0);
            statement.set("modlog_id", caseId);
            statement.set("expires_in", expiresAt);
            statement.set("global", true);
            statement.set("main_group_id", mainGroupId);
        });

        globalMutes.get(mainGroupId).add(new MuteContainer(0, userId, expiresAt, true, mainGroupId));
    }

    /**
     * Unregisters a mute matching the given guild ID and user ID.
     *
     * @param userId  The ID of the user that should be unmuted.
     * @throws SQLException If the unmute fails to delete the mute record from the
     *                      database.
     */
    public void unregisterGlobalMute(long mainGroupId, long userId) throws SQLException {
        if (!globalMutes.containsKey(mainGroupId)) {
            return;
        }

        final boolean[] removedEntities = { false };
        synchronized (globalMutes) {
            globalMutes.get(mainGroupId).removeIf(next -> {
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
            cleanupGlobalMutes(mainGroupId, userId);
        }
    }

    public boolean isGlobalMuted(long mainGroupId, long userId) {
        if (!globalMutes.containsKey(mainGroupId)) {
            return false;
        }

        for (MuteContainer container : globalMutes.get(mainGroupId)) {
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
    public int getTotalAmountOfGlobalMutes() {
        int totalMutes = 0;
        for (Map.Entry<Long, HashSet<MuteContainer>> entry : globalMutes.entrySet()) {
            totalMutes += entry.getValue().size();
        }
        return totalMutes;
    }

    public HashMap<Long, HashSet<MuteContainer>> getGlobalMutes() {
        return globalMutes;
    }

    private final Logger log = LoggerFactory.getLogger(GlobalMuteManager.class);
    private void syncWithGlobalDatabase() {
        log.info("Syncing global mutes with the database...");

        String query = I18n.format(
            "SELECT `{1}`.`mgi`, `{1}`.`target_id`, `{0}`.`expires_in` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`main_group_id` = `{1}`.`mgi` AND `{0}`.`global` = 1;",
            Constants.MUTE_TABLE_NAME, Constants.MGM_LOG_TABLE_NAME);

        try {
            int size = getTotalAmountOfMutes();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long mgi = row.getLong("mgi");

                if (!globalMutes.containsKey(mgi)) {
                    globalMutes.put(mgi, new HashSet<>());
                }

                globalMutes.get(mgi).add(new MuteContainer(row.getLong("guild_id"), row.getLong("target_id"),
                    row.getTimestamp("expires_in"), row.getBoolean("global"), row.getLong("mgi")));
            }

            log.info("Syncing complete! {} global mutes entries was found that has not expired yet",
                getTotalAmountOfMutes() - size);
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }
    private void cleanupGlobalMutes(long mainGroupId, long userId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.MUTE_TABLE_NAME)
            .select(Constants.MUTE_TABLE_NAME + ".modlog_id as id")
            .innerJoin(Constants.MGM_LOG_TABLE_NAME, Constants.MUTE_TABLE_NAME + ".modlog_id",
                Constants.MGM_LOG_TABLE_NAME + ".modlogCase")
            .where(Constants.MGM_LOG_TABLE_NAME + ".mgi", mainGroupId)
            .andWhere(Constants.MGM_LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.MUTE_TABLE_NAME + ".main_group_id", mainGroupId)
            .andWhere(builder -> builder.where(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_MUTE.getId())
                .orWhere(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_TEMP_MUTE.getId()))
            .get();

        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `main_group_id` = ? AND `modlog_id` = ?",
                Constants.MUTE_TABLE_NAME);

            avaire.getDatabase().queryBatch(query, statement -> {
                for (DataRow row : collection) {
                    statement.setLong(1, mainGroupId);
                    statement.setString(2, row.getString("id"));
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
    public int getTotalAmountOfMutes() {
        int totalMutes = 0;
        for (Map.Entry<Long, HashSet<MuteContainer>> entry : globalMutes.entrySet()) {
            totalMutes += entry.getValue().size();
        }
        return totalMutes;
    }

}
