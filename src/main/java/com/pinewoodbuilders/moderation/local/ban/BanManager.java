/*
 * Copyright (c) 2019.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.moderation.local.ban;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.time.Carbon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class BanManager {

    private final Logger log = LoggerFactory.getLogger(BanManager.class);
    private final HashMap<Long, HashSet<BanContainer>> bans = new HashMap<>();

    private final Xeus avaire;

    /**
     * Creates the ban manager instance with the given Xeus
     * application instance, the ban manager will sync the
     * bans entities from the database into memory.
     *
     * @param avaire The main Xeus instance.
     */
    public BanManager(Xeus avaire) {
        this.avaire = avaire;

        syncWithDatabase();
    }

    /**
     * Registers a ban using the given case ID, guild ID, and user ID,
     * if a null value is given for the expire date, the ban will be
     * registered as a permanent ban, however if a valid carbon
     * instance is given that is set in the future, the ban
     * will automatically be reversed once the time is up.
     * <p>
     * If a ban record already exists for the given guild and user IDs,
     * the record will be unband before the new ban is applied, this
     * helps ensure that a user can only have one ban per guild.
     *
     * @param caseId    The ID of the modlog case that triggered the ban action.
     * @param guildId   The ID of the guild the ban should be registered to.
     * @param userId    The ID of the user that was band.
     * @param expiresAt The time the ban should be automatically unband, or {@code NULL} to make the ban permanent.
     * @throws SQLException If the ban fails to be registered with the database, or
     *                      existing bans for the given guild and user IDs fails
     *                      to be removed before the new ban is registered.
     */
    public void registerBan(String caseId, long guildId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!bans.containsKey(guildId)) {
            bans.put(guildId, new HashSet<>());
        }

        if (isBanned(guildId, userId)) {
            unregisterBan(guildId, userId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.BAN_TABLE_NAME)
            .insert(statement -> {
                statement.set("guild_id", guildId);
                statement.set("modlog_id", caseId);
                statement.set("expires_in", expiresAt);
            });

        bans.get(guildId).add(new BanContainer(guildId, userId, expiresAt));
    }

    /**
     * Unregisters a ban matching the given guild ID and user ID.
     *
     * @param guildId The ID of the guild the ban should've been registered to.
     * @param userId  The ID of the user that should be unband.
     * @throws SQLException If the unban fails to delete the ban record from the database.
     */
    public void unregisterBan(long guildId, long userId) throws SQLException {
        if (!bans.containsKey(guildId)) {
            return;
        }

        final boolean[] removedEntities = {false};
        synchronized (bans) {
            bans.get(guildId).removeIf(next -> {
                if (!next.isSame(guildId, userId)) {
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
            cleanupBans(guildId, userId);
        }
    }

    /**
     * Checks if there are any ban record that exists
     * using the given guild and user IDs.
     *
     * @param guildId The ID of the guild that should be checked.
     * @param userId  The ID of the user that should be band.
     * @return {@code True} if a user with the given ID is band on a server
     * with the given guild ID, {@code False} otherwise.
     */
    public boolean isBanned(long guildId, long userId) {
        if (!bans.containsKey(guildId)) {
            return false;
        }

        for (BanContainer container : bans.get(guildId)) {
            if (container.isSame(guildId, userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the total amount of bans currently stored in memory,
     * this includes permanent and temporary bans.
     *
     * @return The total amount of bans stored.
     */
    public int getTotalAmountOfBans() {
        int totalbans = 0;
        for (Map.Entry<Long, HashSet<BanContainer>> entry : bans.entrySet()) {
            totalbans += entry.getValue().size();
        }
        return totalbans;
    }

    /**
     * Gets the map of bans currently stored, where the key is the guild ID for
     * the bans, and the value is a set of ban containers, which holds
     * the information about each individual ban.
     *
     * @return The complete map of bans currently stored.
     */
    public HashMap<Long, HashSet<BanContainer>> getBans() {
        return bans;
    }

    private void syncWithDatabase() {
        log.info("Syncing bans with the database...");

        String query = I18n.format("SELECT `{1}`.`guild_id`, `{1}`.`target_id`, `{0}`.`expires_in` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`guild_id` = `{1}`.`guild_id`;",
            Constants.BAN_TABLE_NAME, Constants.LOG_TABLE_NAME
        );

        try {
            int size = getTotalAmountOfBans();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long guildId = row.getLong("guild_id");

                if (!bans.containsKey(guildId)) {
                    bans.put(guildId, new HashSet<>());
                }

                bans.get(guildId).add(new BanContainer(
                    row.getLong("guild_id"),
                    row.getLong("target_id"),
                    row.getTimestamp("expires_in")
                ));
            }

            log.info("Syncing complete! {} ban entries was found that has not expired yet",
                getTotalAmountOfBans() - size
            );
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    private void cleanupBans(long guildId, long userId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.BAN_TABLE_NAME)
            .select(Constants.BAN_TABLE_NAME + ".modlog_id as id")
            .innerJoin(
                Constants.LOG_TABLE_NAME,
                Constants.BAN_TABLE_NAME + ".modlog_id",
                Constants.LOG_TABLE_NAME + ".modlogCase"
            )
            .where(Constants.LOG_TABLE_NAME + ".guild_id", guildId)
            .andWhere(Constants.LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.BAN_TABLE_NAME + ".guild_id", guildId)
            .andWhere(builder -> builder
                .where(Constants.LOG_TABLE_NAME + ".type", ModlogType.BAN.getId())
                .orWhere(Constants.LOG_TABLE_NAME + ".type", ModlogType.TEMP_BAN.getId())
            ).get();

        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `guild_id` = ? AND `modlog_id` = ?",
                Constants.BAN_TABLE_NAME
            );

            avaire.getDatabase().queryBatch(query, statement -> {
                for (DataRow row : collection) {
                    statement.setLong(1, guildId);
                    statement.setString(2, row.getString("id"));
                    statement.addBatch();
                }
            });
        }
    }
}
