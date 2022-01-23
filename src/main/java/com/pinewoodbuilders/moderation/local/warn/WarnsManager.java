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

package com.pinewoodbuilders.moderation.local.warn;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class WarnsManager {

    private final Logger log = LoggerFactory.getLogger(WarnsManager.class);
    private final HashMap <Long, HashMap <Long, HashSet <WarnContainer>>> warns = new HashMap <>();

    private final Xeus avaire;

    /**
     * Creates the warn manager instance with the given Xeus
     * application instance, the warn manager will sync the
     * warns entities from the database into memory.
     *
     * @param avaire The main Xeus instance.
     */
    public WarnsManager(Xeus avaire) {
        this.avaire = avaire;

        syncWithDatabase();
    }

    /**
     * Registers a warn using the given case ID, guild ID, and user ID,
     * if a null value is given for the expire date, the warn will be
     * registered as a permanent warn, however if a valid carbon
     * instance is given that is set in the future, the warn
     * will automatically be reversed once the time is up.
     * <p>
     * If a warn record already exists for the given guild and user IDs,
     * the record will be unwarned before the new warn is applied, this
     * helps ensure that a user can only have one warn per guild.
     *
     * @param caseId    The ID of the modlog case that triggered the warn action.
     * @param guildId   The ID of the guild the warn should be registered to.
     * @param userId    The ID of the user that was warnd.
     * @param expiresAt The time the warn should be automatically unwarnd, or {@code NULL} to make the warn permanent.
     * @throws SQLException If the warn fails to be registered with the database, or
     *                      existing warns for the given guild and user IDs fails
     *                      to be removed before the new warn is registered.
     */
    public void registerWarn(String caseId, long guildId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!warns.containsKey(guildId)) {
            warns.put(guildId, new HashMap <>());
        }

        if (!warns.get(guildId).containsKey(userId)) {
            warns.get(guildId).put(userId, new HashSet <>());
        }

        avaire.getDatabase().newQueryBuilder(Constants.WARNINGS_TABLE_NAME)
            .insert(statement -> {
                statement.set("guild_id", guildId);
                statement.set("modlog_id", caseId);
                statement.set("expires_in", expiresAt);
            });

        warns.get(guildId).get(userId).add(new WarnContainer(guildId, userId, expiresAt, caseId));
    }

    /**
     * Unregisters a warn matching the given guild ID and user ID.
     *
     * @param guildId The ID of the guild the warn should've been registered to.
     * @param userId  The ID of the user that should be unwarnd.
     * @throws SQLException If the unwarn fails to delete the warn record from the database.
     */
    public void unregisterWarn(long guildId, long userId, String caseId) throws SQLException {
        if (!warns.containsKey(guildId)) {
            return;
        }

        if (!warns.get(guildId).containsKey(userId)) {
            return;
        }

        AtomicBoolean removedEntities = new AtomicBoolean();

        warns.get(guildId).get(userId).removeIf(next -> {
            if (next.isSame(guildId, userId, caseId)) {
                if (next.getSchedule() != null) {
                    next.cancelSchedule();
                }

                removedEntities.set(true);

            } else {
                removedEntities.set(false);
            }

            return removedEntities.get();
        });


        if (removedEntities.get()) {
            cleanupWarns(guildId, userId, caseId);
        }
    }

    /**
     * Get all warns a user has.
     *
     * @param guildId The ID of the guild that should be checked.
     * @param userId  The ID of the user that should be warnd.
     * @return {@code True} if a user with the given ID is warnd on a server
     * with the given guild ID, {@code False} otherwise.
     */
    public HashSet <WarnContainer> getWarns(long guildId, long userId) {
        if (!warns.containsKey(guildId)) {
            return new HashSet <>();
        }

        if (!warns.get(guildId).containsKey(userId)) {
            return new HashSet <>();
        }

        return warns.get(guildId).get(userId);
    }

    /**
     * Gets the total amount of warns currently stored in memory,
     * this includes permanent and temporary warns.
     *
     * @return The total amount of warns stored.
     */
    public int getTotalAmountOfWarns() {
        return warns.size();
    }

    /**
     * Gets the map of warns currently stored, where the key is the guild ID for
     * the warns, and the value is a set of warn containers, which holds
     * the information about each individual warn.
     *
     * @return The complete map of warns currently stored.
     */
    public HashMap <Long, HashMap <Long, HashSet <WarnContainer>>> getWarns() {
        return warns;
    }

    private void syncWithDatabase() {
        log.info("Syncing warns with the database...");

        String query = I18n.format("SELECT `{1}`.`guild_id`, `{0}`.`modlog_id`, `{1}`.`target_id`, `{0}`.`expires_in` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`guild_id` = `{1}`.`guild_id` AND `{1}`.`pardon` = 0;",
            Constants.WARNINGS_TABLE_NAME, Constants.LOG_TABLE_NAME
        );

        try {
            int size = getTotalAmountOfWarns();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long guildId = row.getLong("guild_id");
                long targetId = row.getLong("target_id");


                if (!warns.containsKey(guildId)) {
                    warns.put(guildId, new HashMap <>());
                }

                if (!warns.get(guildId).containsKey(targetId)) {
                    warns.get(guildId).put(targetId, new HashSet <>());
                }

                warns.get(guildId).get(targetId).add(new WarnContainer(
                    row.getLong("guild_id"),
                    row.getLong("target_id"),
                    row.getTimestamp("expires_in"),
                    row.getString("modlog_id"))
                );
            }

            log.info("Syncing complete! {} warn entries was found that has not expired yet...",
                getTotalAmountOfWarns() - size
            );
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    private void cleanupWarns(long guildId, long userId, String caseId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.WARNINGS_TABLE_NAME)
            .select(Constants.WARNINGS_TABLE_NAME + ".modlog_id as id")
            .innerJoin(
                Constants.LOG_TABLE_NAME,
                Constants.WARNINGS_TABLE_NAME + ".modlog_id",
                Constants.LOG_TABLE_NAME + ".modlogCase"
            )
            .where(Constants.LOG_TABLE_NAME + ".guild_id", guildId)
            .andWhere(Constants.LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.WARNINGS_TABLE_NAME + ".guild_id", guildId)
            .andWhere(Constants.LOG_TABLE_NAME + ".type", ModlogType.WARN.getId())
            .andWhere(Constants.WARNINGS_TABLE_NAME + ".modlog_id", caseId)
            .get();
        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `guild_id` = ? AND `modlog_id` = ?",
                Constants.WARNINGS_TABLE_NAME
            );

//            String query2 = String.format("UPDATE `%s` SET `pardon` = 1 WHERE `guild_id` = ? AND `modlogCase` = ? AND `pardon` = ?",
//                Constants.LOG_TABLE_NAME
//            );

            avaire.getDatabase().queryBatch(query, statement -> {
                statement.setLong(1, guildId);
                statement.setString(2, caseId);
                statement.addBatch();
            });

//            avaire.getDatabase().queryBatch(query2, statement -> {
//                statement.setLong(1, guildId);
//                statement.setString(2, caseId);
//                statement.setInt(3, 0);
//                statement.addBatch();
//            });
        }

    }
}
