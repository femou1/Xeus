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

package com.pinewoodbuilders.database.transformers;

import com.google.gson.reflect.TypeToken;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.database.transformers.Transformer;
import com.pinewoodbuilders.database.collection.DataRow;

import java.util.ArrayList;
import java.util.List;

public class GlobalSettingsTransformer extends Transformer {

    // Global Settings
    private long mainGroupId = 0;
    private String mainGroupName;

    private final List<String> globalFilterExact = new ArrayList<>();
    private final List<String> globalFilterWildcard = new ArrayList<>();
    private long globalFilterLogChannel;

    // Global Settings
    private long appealsDiscordId = 0;
    private long mgmLogsId = 0;

    private String globalModlogChannel = null;
    private int globalModlogCase = 0;
    private boolean newWarnSystem = false;
    private long moderationServerId = 0;

    public GlobalSettingsTransformer(DataRow data) {
        super(data);
        if (hasData()) {
            mainGroupId = data.getLong("main_group_id");
            mainGroupName = data.getString("main_group_name");

            globalFilterLogChannel = data.getLong("global_filter_log_channel");

            appealsDiscordId = data.getLong("appeals_discord_id");
            mgmLogsId = data.getLong("mgm_logs");

            globalModlogChannel = data.getString("global_modlog");
            globalModlogCase = data.getInt("global_modlog_case");

            newWarnSystem = data.getBoolean("new_warn_system");

            moderationServerId = data.getLong("moderation_server_id");

            if (data.getString("global_filter_exact", null) != null) {
                List<String> dbFilter = Xeus.gson.fromJson(data.getString("global_filter_exact"),
                        new TypeToken<List<String>>() {
                        }.getType());

                globalFilterExact.addAll(dbFilter);
            }

            if (data.getString("global_filter_wildcard", null) != null) {
                List<String> dbFilter = Xeus.gson.fromJson(data.getString("global_filter_wildcard"),
                        new TypeToken<List<String>>() {
                        }.getType());

                globalFilterWildcard.addAll(dbFilter);
            }
            reset();
        }
    }

    public String getMainGroupName() {
        return mainGroupName;
    }

    public void setMainGroupName(String mainGroupName) {
        this.mainGroupName = mainGroupName;
    }
    public long getMainGroupId() {
        return this.mainGroupId;
    }

    public void setMainGroupId(long mainGroupId) {
        this.mainGroupId = mainGroupId;
    }

        public List<String> getGlobalFilterExact() {
        return this.globalFilterExact;
    }

    public List<String> getGlobalFilterWildcard() {
        return this.globalFilterWildcard;
    }

    public long getGlobalFilterLogChannel() {
        return this.globalFilterLogChannel;
    }

    public void setGlobalFilterLogChannel(long globalFilterLogChannel) {
        this.globalFilterLogChannel = globalFilterLogChannel;
    }


    public long getAppealsDiscordId() {
        return this.appealsDiscordId;
    }

    public void setAppealsDiscordId(long appealsDiscordId) {
        this.appealsDiscordId = appealsDiscordId;
    }

    public long getMgmLogsId() {
        return this.mgmLogsId;
    }

    public void setMgmLogsId(long mgmLogsId) {
        this.mgmLogsId = mgmLogsId;
    }

    public String getGlobalModlogChannel() {
        return globalModlogChannel;
    }

    public void setGlobalModlogChannel(String globalModlogChannel) {
        this.globalModlogChannel = globalModlogChannel;
    }

    public int getGlobalModlogCase() {
        return globalModlogCase;
    }

    public void setGlobalModlogCase(int globalModlogCase) {
        this.globalModlogCase = globalModlogCase;
    }

    public boolean getNewWarnSystem() {
        return newWarnSystem;
    }

    public void setNewWarnSystem(boolean newWarnSystem) {
        this.newWarnSystem = newWarnSystem;
    }

    public boolean getGlobalFilter() {
        return globalFilterExact.size() > 0 || globalFilterWildcard.size() > 0;
    }

    public long getModerationServerId() {
        return moderationServerId;
    }

    public void setModerationServerId(long moderationServerId) {
        this.moderationServerId = moderationServerId;
    }
}
