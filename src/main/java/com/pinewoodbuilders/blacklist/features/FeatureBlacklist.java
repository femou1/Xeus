/*
 * Copyright (c) 2018.
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

package com.pinewoodbuilders.blacklist.features;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.query.ChangeableStatement;
import com.pinewoodbuilders.time.Carbon;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("SuspiciousMethodCalls")
public class FeatureBlacklist {

    private final Xeus avaire;
    private final List<FeatureBlacklistEntity> blacklist;

    /**
     * Creates a new blacklist instance.
     *
     * @param avaire The main avaire instance.
     */
    public FeatureBlacklist(Xeus avaire) {
        this.avaire = avaire;

        this.blacklist = new ArrayList<>();
    }

    /**
     * Checks if the author of the message, of the message was
     * sent in a guild, that the guild is on the blacklist.
     *
     * @param message The message that should be checked.
     * @return <code>True</code> if either the user, or the entire
     * server is blacklisted, <code>False</code> otherwise.
     */
    public boolean isBlacklisted(@Nonnull Message message, FeatureScope featureScope) {
        return isBlacklisted(message.getAuthor(), message.getGuild().getIdLong(), featureScope);
    }

    /**
     * Checks if the given user is on the blacklist.
     *
     * @param user The user that should be checked.
     * @return <code>True</code> if the user is on the blacklist, <code>False</code> otherwise.
     */
    public boolean isBlacklisted(@Nonnull User user, long guildId,  FeatureScope featureScope) {
        FeatureBlacklistEntity entity = getEntity(user.getIdLong(), featureScope, guildId);
        return entity != null && entity.isBlacklisted();
    }

    /**
     * Adds the given user to the blacklist with the given reason, the blacklist
     * @param reason The reason for the user being added to the blacklist.
     */
    public void addUser(@Nonnull Member member, @Nullable String reason, long guildId, FeatureScope featureScope) {
        addIdToBlacklist(featureScope, member.getIdLong(), reason, guildId);
    }

    /**
     * Removes the blacklist record with the given ID.
     *
     * @param id The ID to remove from teh blacklist.
     */
    public void remove(long id, long guildId, FeatureScope featureScope) {
        User u = avaire.getShardManager().getUserById(id);
        if (u == null) {
            return;
        }
        if (!isBlacklisted(u, guildId, featureScope)) {
            return;
        }

        Iterator<FeatureBlacklistEntity> iterator = blacklist.iterator();
        while (iterator.hasNext()) {
            FeatureBlacklistEntity next = iterator.next();

            if (next.getId() == id && next.getGuildId() == guildId) {
                iterator.remove();
                break;
            }
        }

        try {
            avaire.getDatabase().newQueryBuilder(Constants.FEATURE_BLACKLIST_TABLE_NAME)
                .where("id", id)
                .delete();
        } catch (SQLException e) {
            Xeus.getLogger().error("Failed to sync blacklist with the database: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the blacklist entity for the given ID.
     *
     * @param id The ID to get the blacklist entity for.
     * @return Possibly-null, the blacklist entity matching the given ID.
     */
    @Nullable
    public FeatureBlacklistEntity getEntity(long id, long guild) {
        return getEntity(id, null, guild);
    }

    /**
     * Gets the blacklist entity for the given ID, matching the given scope.
     *
     * @param id    The ID to get the blacklist entity for.
     * @param featureScope The scope that the blacklist entity should belong to.
     * @return Possible-null, the blacklist entity matching the given ID and scope.
     */
    @Nullable
    public FeatureBlacklistEntity getEntity(long id, @Nullable FeatureScope featureScope, Long guild) {
        for (FeatureBlacklistEntity entity : blacklist) {
            if (entity.getGuildId() == 1 && entity.getScope().getId() == 0) {
                return entity;
            } else if (entity.getId() == id && entity.getGuildId() == guild) {
                if (featureScope != null && featureScope != entity.getScope() && entity.getScope().getId() != 1) {
                    continue;
                }
                return entity;
            }
        }
        return null;
    }

    /**
     * Adds the ID to the blacklist with the given scope and reason.
     *
     * @param featureScope  The scope to register the blacklist record under.
     * @param id     The ID that should be added to the blacklist.
     * @param reason The reason that the ID was added to the blacklist.
     */
    public void addIdToBlacklist(FeatureScope featureScope, final long id, final @Nullable String reason, Long guild) {
        addIdToBlacklist(featureScope, id, reason, null, guild);
    }

    /**
     * Adds the ID to the blacklist with the given scope, reason, and expire time.
     *
     * @param featureScope     The scope to register the blacklist record under.
     * @param id        The ID that should be added to the blacklist.
     * @param reason    The reason that the ID was added to the blacklist.
     * @param expiresIn The carbon time instance for when the entity should expire.
     */
    public void addIdToBlacklist(FeatureScope featureScope, final long id, final @Nullable String reason, @Nullable Carbon expiresIn, Long guild) {
        FeatureBlacklistEntity entity = getEntity(id, featureScope, guild);
        if (entity != null) {
            blacklist.remove(entity);
        }

        blacklist.add(new FeatureBlacklistEntity(featureScope, id, reason, expiresIn, guild));

        try {
            avaire.getDatabase().newQueryBuilder(Constants.FEATURE_BLACKLIST_TABLE_NAME)
                .where("id", id)
                .andWhere("guild_id", guild)
                .andWhere("type", featureScope.getId())
                .delete();

            avaire.getDatabase().newQueryBuilder(Constants.FEATURE_BLACKLIST_TABLE_NAME)
                .useAsync(true)
                .insert((ChangeableStatement statement) -> {
                    statement.set("id", id);
                    statement.set("type", featureScope.getId());
                    statement.set("expires_in", expiresIn);
                    statement.set("guild_id", guild);

                    if (expiresIn == null) {
                        statement.set("expires_in", Carbon.now().addYears(10));
                    }

                    if (reason != null) {
                        statement.set("reason", reason);
                    }
                });
        } catch (SQLException e) {
            Xeus.getLogger().error("Failed to sync blacklist with the database: " + e.getMessage(), e);
        }
    }

    /**
     * Get the all the entities currently on the blacklist, this
     * includes both users and guilds, the type can be checked
     * through the {@link FeatureBlacklistEntity#getScope() scope}.
     *
     * @return The entities currently on the blacklist.
     */
    public List<FeatureBlacklistEntity> getBlacklistEntities() {
        return blacklist;
    }

    /**
     * Syncs the blacklist with the database.
     */
    public synchronized void syncBlacklistWithDatabase() {
        blacklist.clear();
        try {
            Collection collection = avaire.getDatabase().newQueryBuilder(Constants.FEATURE_BLACKLIST_TABLE_NAME)
                .where("expires_in", ">", Carbon.now())
                .get();

            collection.forEach(row -> {
                String id = row.getString("id", null);
                if (id == null) {
                    return;
                }

                try {
                    long longId = Long.parseLong(id);
                    FeatureScope featureScope = FeatureScope.fromId(row.getInt("type", 0));

                    blacklist.add(new FeatureBlacklistEntity(
                        featureScope, longId,
                        row.getString("reason"),
                        row.getTimestamp("expires_in"),
                        row.getLong("guild_id")
                    ));
                } catch (NumberFormatException ignored) {
                    // This is ignored
                }
            });
        } catch (SQLException e) {
            Xeus.getLogger().error("Failed to sync blacklist with the database: " + e.getMessage(), e);
        }
    }

}
