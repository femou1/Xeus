package com.pinewoodbuilders.moderation.global.punishments;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.moderation.global.punishments.globalban.GlobalBanContainer;
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

        if (userId != null && isGlobalBanned(mgi, userId)) {
            unregisterDiscordGlobalBan(mgi, userId);
        }

        if (robloxId != 0 && isRobloxGlobalBanned(mgi,  robloxId)) {
            unregisterRobloxGlobalBan(mgi, robloxId);
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
                long mgi = row.getLong("main_group_id");


                if (!globalBans.containsKey(mgi)) {
                    globalBans.put(mgi, new HashSet<>());
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
     * @throws SQLException If the unmute fails to delete the mute record from the
     *                      database.
     */
    public void unregisterRobloxGlobalBan(long mgi, long robloxId) throws SQLException {
        if (!globalBans.containsKey(mgi)) {
            return;
        }

        final boolean[] removedEntities = {false};
        synchronized (globalBans) {
            globalBans.get(mgi).removeIf(next -> {
                if (!isRobloxGlobalBanned(mgi, robloxId)) {
                    return false;
                }

                removedEntities[0] = true;
                return true;
            });
        }

        if (removedEntities[0]) {
            cleanupRobloxGlobalBan(mgi, robloxId);
        }
    }

    private void cleanupRobloxGlobalBan(long mgi, long robloxId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
            .where("main_group_id", mgi)
            .andWhere("roblox_user_id", robloxId)
            .get();

        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `main_group_id` = ? AND `roblox_user_id` = ?",
                Constants.ANTI_UNBAN_TABLE_NAME);

            avaire.getDatabase().queryBatch(query, statement -> {
                statement.setLong(1, mgi);
                statement.setLong(2, robloxId);
                statement.addBatch();
            });
        }
    }

    public void unregisterDiscordGlobalBan(long mgi, String userId) throws SQLException {
        if (!globalBans.containsKey(mgi)) {
            return;
        }

        final boolean[] removedEntities = {false};
        synchronized (globalBans) {
            globalBans.get(mgi).removeIf(next -> {
                if (!isGlobalBanned(mgi, userId)) {
                    return false;
                }

                removedEntities[0] = true;
                return true;
            });
        }

        if (removedEntities[0]) {
            cleanupDiscordGlobalBan(mgi, userId);
        }
    }

    private void cleanupDiscordGlobalBan(long mgi, String userId) throws SQLException {
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
     * @param mgi    The ID of the guild that should be checked.
     * @return {@code True} if a user with the given ID is muted on a server with
     * the given guild ID, {@code False} otherwise.
     */
    public boolean isGlobalBanned(long mgi, String discordId) {
        if (!globalBans.containsKey(mgi)) {
            return false;
        }

        for (GlobalBanContainer container : globalBans.get(mgi)) {
            if (container.isSame(mgi, discordId)) {
                return true;
            }
        }

        VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchVerificationWithBackup(discordId, true);
        if (ve != null) {
            return isRobloxGlobalBanned(mgi, ve.getRobloxId());
        }
        return false;
    }

    public boolean isRobloxGlobalBanned(long mgi, long robloxId) {
        if (!globalBans.containsKey(mgi)) {
            return false;
        }

        for (GlobalBanContainer container : globalBans.get(mgi)) {
            if (container.isRobloxSame(mgi, robloxId)) {
                return true;
            }
        }
        return false;
    }

}
