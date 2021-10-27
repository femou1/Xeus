package com.pinewoodbuilders.moderation.punishments;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.moderation.punishments.globalban.GlobalBanContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;

public class GlobalPunishmentManager {

    private final Logger log = LoggerFactory.getLogger(GlobalPunishmentManager.class);
    private final HashMap <Long, HashSet <GlobalBanContainer>> globalBans = new HashMap<>();
    private final Xeus avaire;

    public GlobalPunishmentManager(Xeus xeus) {
        this.avaire = xeus;

        syncWithDatabase();
    }

    public HashMap <Long, HashSet <GlobalBanContainer>> getGlobalBans() {
        return globalBans;
    }

    public void registerGlobalBan(String punisherId, long mgi, String userId, long robloxId, String robloxUsername, String reason) throws SQLException {
        if (!globalBans.containsKey(mgi)) {
            globalBans.put(mgi, new HashSet <>());
        }

        if (isGlobalBanned(mgi, userId)) {
            unregisterGlobalBan(mgi, userId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).insert(statement -> {
            statement.set("punisherId", punisherId);
            statement.set("userId", userId);
            statement.set("main_group_id", mgi);
            statement.set("roblox_user_id", robloxId);
            statement.set("roblox_username", robloxUsername);
            statement.set("reason", reason, true);
        });

        globalBans.get(mgi).add(new GlobalBanContainer(reason, robloxId, robloxUsername, mgi, userId, punisherId));
    }

    private void syncWithDatabase() {
        log.info("Syncing global punishments with the database...");

        try {
            Collection rows = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).get();
            long size = rows.size();
            for (DataRow row : rows) {
                if (row.getString("userId") == null) {return;}
                long mgi = row.getLong("main_group_id");


                if (!globalBans.containsKey(mgi)) {
                    globalBans.put(mgi, new HashSet <>());
                }

                globalBans.get(mgi).add(new GlobalBanContainer(row));
            }

            log.info("Syncing complete! {} global bans have been loaded into memory.", size);
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    /**
     * Unregisters a mute matching the given guild ID and user ID.
     *
     * @param guildId The ID of the guild the mute should've been registered to.
     * @param userId  The ID of the user that should be unmuted.
     * @throws SQLException If the unmute fails to delete the mute record from the
     *                      database.
     */
    public void unregisterGlobalBan(long guildId, String userId) throws SQLException {
        if (!globalBans.containsKey(guildId)) {
            return;
        }

        final boolean[] removedEntities = {false};
        synchronized (globalBans) {
            globalBans.get(guildId).removeIf(next -> {
                if (!next.isSame(guildId, userId)) {
                    return false;
                }

                removedEntities[0] = true;
                return true;
            });
        }

        if (removedEntities[0]) {
            cleanupGlobalBan(guildId, userId);
        }
    }

    private void cleanupGlobalBan(long mgi, String userId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
            .where("main_group_id", mgi)
            .andWhere("userId", userId)
            .get();

        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `main_group_id` = ? AND `userId` = ?",
                Constants.ANTI_UNBAN_TABLE_NAME);

            avaire.getDatabase().queryBatch(query, statement -> {
                statement.setLong(1, mgi);
                statement.setString(2, userId);
                statement.addBatch();
            });
        }
    }

    /**
     * Checks if there are any mute record that exists using the given guild and
     * user IDs.
     *
     * @param mgi The ID of the guild that should be checked.
     * @param userId  The ID of the user that should be muted.
     * @return {@code True} if a user with the given ID is muted on a server with
     * the given guild ID, {@code False} otherwise.
     */
    public boolean isGlobalBanned(long mgi, String userId) {
        if (!globalBans.containsKey(mgi)) {
            return false;
        }

        for (GlobalBanContainer container : globalBans.get(mgi)) {
            if (container.isSame(mgi, userId)) {
                return true;
            }
        }

        return false;
    }
}
