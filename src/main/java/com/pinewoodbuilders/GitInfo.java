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

package com.pinewoodbuilders;

import com.pinewoodbuilders.contracts.config.PropertyConfiguration;

@SuppressWarnings("WeakerAccess")
public class GitInfo extends PropertyConfiguration {

    private static GitInfo instance;

    public final String branch;
    public final String commitId;
    public final String commitIdAbbrev;
    public final String commitUserName;
    public final String commitUserEmail;
    public final String commitMessageFull;
    public final String commitMessageShort;
    public final String commitTime;

    private GitInfo() {
        loadProperty(getClass().getClassLoader(), "git.properties");

        this.branch = String.valueOf(properties.getOrDefault("git.branch", ""));
        this.commitId = String.valueOf(properties.getOrDefault("git.commit.id", ""));
        this.commitIdAbbrev = String.valueOf(properties.getOrDefault("git.commit.id.abbrev", ""));
        this.commitUserName = String.valueOf(properties.getOrDefault("git.commit.user.name", ""));
        this.commitUserEmail = String.valueOf(properties.getOrDefault("git.commit.user.email", ""));
        this.commitMessageFull = String.valueOf(properties.getOrDefault("git.commit.message.full", ""));
        this.commitMessageShort = String.valueOf(properties.getOrDefault("git.commit.message.short", ""));
        this.commitTime = String.valueOf(properties.getOrDefault("git.commit.time", "Unknown"));
    }

    public static GitInfo getGitInfo() {
        if (instance == null) {
            instance = new GitInfo();
        }
        return instance;
    }
}
