package com.pinewoodbuilders.moderation.global.globalmute;

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

    private final HashMap <Long, HashSet <GlobalMuteContainer>> globalMutes = new HashMap<>();

    public void registerGlobalMute(long guildId, String caseId, long mainGroupId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!globalMutes.containsKey(mainGroupId)) {
            globalMutes.put(mainGroupId, new HashSet<>());
        }

        if (isGlobalMuted(mainGroupId, userId, guildId)) {
            unregisterGlobalMute(mainGroupId, userId, guildId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_MUTE_TABLE_NAME).insert(statement -> {
            statement.set("guild_id", guildId);
            statement.set("modlog_id", caseId);
            statement.set("expires_in", expiresAt);
            statement.set("main_group_id", mainGroupId);
        });

        globalMutes.get(mainGroupId).add(new GlobalMuteContainer(guildId, userId, expiresAt, mainGroupId));
    }

    /**
     * Unregisters a mute matching the given guild ID and user ID.
     *
     * @param userId  The ID of the user that should be unmuted.
     * @param guildId
     * @throws SQLException If the unmute fails to delete the mute record from the
     *                      database.
     */
    public void unregisterGlobalMute(long mainGroupId, long userId, long guildId) throws SQLException {
        if (!globalMutes.containsKey(mainGroupId)) {
            return;
        }

        final boolean[] removedEntities = { false };
        synchronized (globalMutes) {
            globalMutes.get(mainGroupId).removeIf(next -> {
                if (!next.isSame(guildId, userId, mainGroupId)) {
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
            cleanupGlobalMutes(mainGroupId, userId, guildId);
        }
    }

    public boolean isGlobalMuted(long mainGroupId, long userId, long guildId) {
        if (!globalMutes.containsKey(mainGroupId)) {
            return false;
        }

        for (GlobalMuteContainer container : globalMutes.get(mainGroupId)) {
            if (container.isSame(guildId, userId, mainGroupId)) {
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
        for (Map.Entry <Long, HashSet <GlobalMuteContainer>> entry : globalMutes.entrySet()) {
            totalMutes += entry.getValue().size();
        }
        return totalMutes;
    }

    public HashMap<Long, HashSet<GlobalMuteContainer>> getGlobalMutes() {
        return globalMutes;
    }

    private final Logger log = LoggerFactory.getLogger(GlobalMuteManager.class);
    private void syncWithGlobalDatabase() {
        log.info("Syncing global mutes with the database...");

        String query = I18n.format(
            "SELECT `{1}`.`mgi`, `{1}`.`target_id`, `{0}`.`expires_in`, `{0}`.`guild_id` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`main_group_id` = `{1}`.`mgi`;",
            Constants.GLOBAL_MUTE_TABLE_NAME, Constants.MGM_LOG_TABLE_NAME);

        try {
            int size = getTotalAmountOfMutes();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long mgi = row.getLong("mgi");

                if (!globalMutes.containsKey(mgi)) {
                    globalMutes.put(mgi, new HashSet<>());
                }

                globalMutes.get(mgi).add(new GlobalMuteContainer(
                    row.getLong("guild_id"),
                    row.getLong("target_id"),
                    row.getTimestamp("expires_in"),
                    row.getLong("mgi")
                ));
            }

            log.info("Syncing complete! {} global mutes entries was found that has not expired yet",
                getTotalAmountOfMutes() - size);
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }
    private void cleanupGlobalMutes(long mainGroupId, long userId, long guildId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_MUTE_TABLE_NAME)
            .select(Constants.GLOBAL_MUTE_TABLE_NAME + ".modlog_id as id")
            .innerJoin(Constants.MGM_LOG_TABLE_NAME, Constants.GLOBAL_MUTE_TABLE_NAME + ".modlog_id",
                Constants.MGM_LOG_TABLE_NAME + ".modlogCase")
            .where(Constants.MGM_LOG_TABLE_NAME + ".mgi", mainGroupId)
            .andWhere(Constants.MGM_LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.GLOBAL_MUTE_TABLE_NAME + ".main_group_id", mainGroupId)
            .andWhere(builder -> builder.where(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_MUTE.getId())
                .orWhere(Constants.MGM_LOG_TABLE_NAME + ".type", GlobalModlogType.GLOBAL_TEMP_MUTE.getId()))
            .andWhere(Constants.GLOBAL_MUTE_TABLE_NAME + ".guild_id", guildId)
            .get();

        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `main_group_id` = ? AND `modlog_id` = ? AND `guild_id` = ?",
                Constants.GLOBAL_MUTE_TABLE_NAME);

            avaire.getDatabase().queryBatch(query, statement -> {
                for (DataRow row : collection) {
                    statement.setLong(1, mainGroupId);
                    statement.setString(2, row.getString("id"));
                    statement.setLong(3, guildId);
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
        for (Map.Entry<Long, HashSet<GlobalMuteContainer>> entry : globalMutes.entrySet()) {
            totalMutes += entry.getValue().size();
        }
        return totalMutes;
    }

}
