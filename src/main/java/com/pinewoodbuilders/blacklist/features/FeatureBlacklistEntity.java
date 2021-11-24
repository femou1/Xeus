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

import com.pinewoodbuilders.contracts.debug.Evalable;
import com.pinewoodbuilders.time.Carbon;

import javax.annotation.Nullable;

@SuppressWarnings("WeakerAccess")
public class FeatureBlacklistEntity extends Evalable {

    private final FeatureScope featureScope;
    private final long id;
    private final Carbon expiresIn;
    private final String reason;
    private final long guildId;

    /**
     * Create a new blacklist entity with the given scope, id, and expires time.
     *
     * @param featureScope     The scope for the blacklist entity.
     * @param id        The ID that the blacklist entity should be linked to.
     * @param reason    The reason the entity was blacklisted.
     * @param expiresIn The carbon time instance for when the blacklist entity should expire.
     */
    FeatureBlacklistEntity(FeatureScope featureScope, long id, @Nullable String reason, @Nullable Carbon expiresIn, long guildId) {
        this.featureScope = featureScope;
        this.id = id;
        this.reason = reason;
        this.expiresIn = expiresIn;
        this.guildId = guildId;
    }

    /**
     * Create a new blacklist entity with the given scope, and id. The blacklist
     * entity will be created with a <code>null</code> expire time, making
     * the blacklist entity last forever.
     *
     * @param featureScope  The scope for the blacklist entity.
     * @param id     The ID that the blacklist entity should be linked to.
     * @param reason The reason the entity was blacklisted.
     */
    public FeatureBlacklistEntity(FeatureScope featureScope, long id, @Nullable String reason, long guildId) {
        this(featureScope, id, reason, null, guildId);
    }

    /**
     * The scope for the blacklist entity.
     *
     * @return The scope for the blacklist entity.
     */
    public FeatureScope getScope() {
        return featureScope;
    }

    /**
     * The ID that the blacklist entity is linked to.
     *
     * @return The ID that the blacklist entity is linked to.
     */
    public long getId() {
        return id;
    }

    /**
     * The guildId that the blacklist entity is linked to.
     *
     * @return The guildId that the blacklist entity is linked to.
     */
    public long getGuildId() {
        return guildId;
    }

    /**
     * Checks if the blacklist entity is still blacklisted.
     *
     * @return <code>True</code> if the user is still blacklisted, <code>False</code> otherwise.
     */
    public boolean isBlacklisted() {
        return featureScope.equals(FeatureScope.PIA_GLOBAL) || (expiresIn != null && expiresIn.isFuture());
    }

    /**
     * Gets the reason the entity was blacklist form, or null.
     *
     * @return Possibly-null, the reason the entity was blacklisted for.
     */
    @Nullable
    public String getReason() {
        return reason;
    }
}
