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

package com.pinewoodbuilders.moderation.local.watch;

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
import java.util.Map;

public class WatchManager {

    private final Logger log = LoggerFactory.getLogger(WatchManager.class);
    private final HashMap<Long, HashSet<WatchContainer>> OnWatchs = new HashMap<>();

    private final Xeus avaire;

    /**
     * Creates the OnWatch manager instance with the given Xeus
     * application instance, the OnWatch manager will sync the
     * OnWatchs entities from the database into memory.
     *
     * @param avaire The main Xeus instance.
     */
    public WatchManager(Xeus avaire) {
        this.avaire = avaire;

        syncWithDatabase();
    }

    /**
     * Registers a OnWatch using the given case ID, guild ID, and user ID,
     * if a null value is given for the expire date, the OnWatch will be
     * registered as a permanent OnWatch, however if a valid carbon
     * instance is given that is set in the future, the OnWatch
     * will automatically be reversed once the time is up.
     * <p>
     * If a OnWatch record already exists for the given guild and user IDs,
     * the record will be unOnWatchd before the new OnWatch is applied, this
     * helps ensure that a user can only have one OnWatch per guild.
     *
     * @param caseId    The ID of the modlog case that triggered the OnWatch action.
     * @param guildId   The ID of the guild the OnWatch should be registered to.
     * @param userId    The ID of the user that was OnWatchd.
     * @param expiresAt The time the OnWatch should be automatically unOnWatchd, or {@code NULL} to make the OnWatch permanent.
     * @throws SQLException If the OnWatch fails to be registered with the database, or
     *                      existing OnWatchs for the given guild and user IDs fails
     *                      to be removed before the new OnWatch is registered.
     */
    public void registerOnWatch(String caseId, long guildId, long userId, @Nullable Carbon expiresAt) throws SQLException {
        if (!OnWatchs.containsKey(guildId)) {
            OnWatchs.put(guildId, new HashSet<>());
        }

        if (isOnWatchd(guildId, userId)) {
            unregisterOnWatch(guildId, userId);
        }

        avaire.getDatabase().newQueryBuilder(Constants.ON_WATCH_TABLE_NAME)
            .insert(statement -> {
                statement.set("guild_id", guildId);
                statement.set("modlog_id", caseId);
                statement.set("expires_in", expiresAt);
            });

        OnWatchs.get(guildId).add(new WatchContainer(guildId, userId, expiresAt));
    }

    /**
     * Unregisters a OnWatch matching the given guild ID and user ID.
     *
     * @param guildId The ID of the guild the OnWatch should've been registered to.
     * @param userId  The ID of the user that should be unOnWatchd.
     * @throws SQLException If the unOnWatch fails to delete the OnWatch record from the database.
     */
    public void unregisterOnWatch(long guildId, long userId) throws SQLException {
        if (!OnWatchs.containsKey(guildId)) {
            return;
        }

        final boolean[] removedEntities = {false};
        synchronized (OnWatchs) {
            OnWatchs.get(guildId).removeIf(next -> {
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
            cleanupOnWatchs(guildId, userId);
        }
    }

    /**
     * Checks if there are any OnWatch record that exists
     * using the given guild and user IDs.
     *
     * @param guildId The ID of the guild that should be checked.
     * @param userId  The ID of the user that should be OnWatchd.
     * @return {@code True} if a user with the given ID is OnWatchd on a server
     *         with the given guild ID, {@code False} otherwise.
     */
    public boolean isOnWatchd(long guildId, long userId) {
        if (!OnWatchs.containsKey(guildId)) {
            return false;
        }

        for (WatchContainer container : OnWatchs.get(guildId)) {
            if (container.isPublic()) {
                return true;
            }
            
            if (container.isSame(guildId, userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the total amount of OnWatchs currently stored in memory,
     * this includes permanent and temporary OnWatchs.
     *
     * @return The total amount of OnWatchs stored.
     */
    public int getTotalAmountOfOnWatchs() {
        int totalOnWatchs = 0;
        for (Map.Entry<Long, HashSet<WatchContainer>> entry : OnWatchs.entrySet()) {
            totalOnWatchs += entry.getValue().size();
        }
        return totalOnWatchs;
    }

    /**
     * Gets the map of OnWatchs currently stored, where the key is the guild ID for
     * the OnWatchs, and the value is a set of OnWatch containers, which holds
     * the information about each individual OnWatch.
     *
     * @return The complete map of OnWatchs currently stored.
     */
    public HashMap<Long, HashSet<WatchContainer>> getOnWatchs() {
        return OnWatchs;
    }

    private void syncWithDatabase() {
        log.info("Syncing OnWatchs with the database...");

        String query = I18n.format("SELECT `{1}`.`guild_id`, `{1}`.`target_id`, `{0}`.`expires_in` FROM `{0}` INNER JOIN `{1}` ON `{0}`.`modlog_id` = `{1}`.`modlogCase` WHERE `{0}`.`modlog_id` = `{1}`.`modlogCase` AND `{0}`.`guild_id` = `{1}`.`guild_id`;",
            Constants.ON_WATCH_TABLE_NAME, Constants.ON_WATCH_LOG_TABLE_NAME
        );

        try {
            int size = getTotalAmountOfOnWatchs();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long guildId = row.getLong("guild_id");

                if (!OnWatchs.containsKey(guildId)) {
                    OnWatchs.put(guildId, new HashSet<>());
                }

                OnWatchs.get(guildId).add(new WatchContainer(
                    row.getLong("guild_id"),
                    row.getLong("target_id"),
                    row.getTimestamp("expires_in")
                ));
            }

            log.info("Syncing complete! {} OnWatchs entries was found that has not expired yet",
                getTotalAmountOfOnWatchs() - size
            );
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    private void cleanupOnWatchs(long guildId, long userId) throws SQLException {
        Collection collection = avaire.getDatabase().newQueryBuilder(Constants.ON_WATCH_TABLE_NAME)
            .select(Constants.ON_WATCH_TABLE_NAME + ".modlog_id as id")
            .innerJoin(
                Constants.ON_WATCH_LOG_TABLE_NAME,
                Constants.ON_WATCH_TABLE_NAME + ".modlog_id",
                Constants.ON_WATCH_LOG_TABLE_NAME + ".modlogCase"
            )
            .where(Constants.ON_WATCH_LOG_TABLE_NAME + ".guild_id", guildId)
            .andWhere(Constants.ON_WATCH_LOG_TABLE_NAME + ".target_id", userId)
            .andWhere(Constants.ON_WATCH_TABLE_NAME + ".guild_id", guildId)
            .andWhere(builder -> builder
                .where(Constants.ON_WATCH_LOG_TABLE_NAME + ".type", ModlogType.ON_WATCH.getId())
                .orWhere(Constants.ON_WATCH_LOG_TABLE_NAME + ".type", ModlogType.TEMP_ON_WATCH.getId())
            ).get();

        if (!collection.isEmpty()) {
            String query = String.format("DELETE FROM `%s` WHERE `guild_id` = ? AND `modlog_id` = ?",
                Constants.ON_WATCH_TABLE_NAME
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
