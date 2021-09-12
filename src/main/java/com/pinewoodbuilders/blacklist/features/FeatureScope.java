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

import com.pinewoodbuilders.utilities.NumberUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum FeatureScope {

    /**
     * The user scope, used for blacklisting users.
     */
    PIA_GLOBAL(0, "G", "Global Feature Blacklist (Global - MGM Only)"),
    GLOBAL(1, "A", "All Features Blacklist (Local)"),
    REPORTS(2, "R", "Reports"),
    REWARD_REQUESTS(3, "RR", "Reward Requests"),
    PATROL_REMITTANCE(4, "PR", "Patrol Remittance"),
    SUGGESTIONS(5, "S", "Suggestions");

    private final int id;
    private final String prefix;
    private final String name;

    FeatureScope(int id, String prefix, String name) {
        this.id = id;
        this.prefix = prefix;
        this.name = name;
    }

    /**
     * Gets the scope from the given ID, if no valid
     * ID was given, null will be returned instead.
     *
     * @param id The ID that the scope should match.
     * @return Possibly-null, the scope matching the given ID.
     */
    public static FeatureScope fromId(int id) {
        for (FeatureScope featureScope : values()) {
            if (featureScope.getId() == id) {
                return featureScope;
            }
        }
        return null;
    }

    /**
     * /**
     * Parses the given string to a valid scope by comparing
     * the string to scope prefixes and scope IDs.
     *
     * @param string The string that should be parsed.
     * @return Possible-null, the scope matching the given string.
     */
    @Nullable
    public static FeatureScope parse(@Nonnull String string) {
        int parsedInt = NumberUtil.parseInt(string, -1);
        for (FeatureScope featureScope : values()) {
            if (string.equalsIgnoreCase(featureScope.getPrefix())) {
                return featureScope;
            }

            if (parsedInt == featureScope.getId()) {
                return featureScope;
            }
        }
        return null;
    }

    /**
     * Gets the ID of the scope.
     *
     * @return The ID of the scope.
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the prefix of the scope.
     *
     * @return The prefix of the scope.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Gets the name of the scope
     *
     * @return The name of the scope.
     */
    public String getName() {
        return name;
    }
}
