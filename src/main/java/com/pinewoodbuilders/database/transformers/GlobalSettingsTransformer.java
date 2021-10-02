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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.reflect.TypeToken;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.database.transformers.Transformer;
import com.pinewoodbuilders.database.collection.DataRow;
import net.dv8tion.jda.api.entities.Guild;

public class GlobalSettingsTransformer extends Transformer {

    // Global Settings
    private long mainGroupId = 0;
    private String mainGroupName;
    private boolean globalBan;
    private boolean globalKick;
    private boolean globalVerify;
    private boolean globalAntiUnban;
    private boolean globalFilter;
    private List<String> globalFilterExact = new ArrayList<>();
    private List<String> globalFilterWildcard = new ArrayList<>();
    private long globalFilterLogChannel;
    private boolean globalAutomod;
    private int automodMassMention;
    private int automodEmojiSpam;
    private int automodLinkSpam;
    private int automodMessageSpam;
    private int automodImageSpam;
    private int automodCharacterSpam;

    public GlobalSettingsTransformer(Long groupId, DataRow data) {
        super(data);
        if (hasData()) {
        mainGroupId = data.getLong("main_group_id");
        mainGroupName = data.getString("main_group_name");
        globalBan = data.getBoolean("global_ban");
        globalKick = data.getBoolean("global_kick");
        globalVerify = data.getBoolean("global_verify");
        globalAntiUnban = data.getBoolean("global_anti_unban");
        globalFilter = data.getBoolean("global_filter");
        globalFilterLogChannel = data.getLong("global_filter_log_channel");
        globalAutomod = data.getBoolean("global_automod");
        automodMassMention = data.getInt("automod_mass_mention");
        automodEmojiSpam = data.getInt("automod_emoji_spam");
        automodLinkSpam = data.getInt("automod_link_spam");
        automodMessageSpam = data.getInt("automod_message_spam");
        automodImageSpam = data.getInt("automod_image_spam");
        automodCharacterSpam = data.getInt("automod_character_spam");

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



    public String getGroupName() {
        return this.mainGroupName;
    }

    public void setGroupName(String mainGroupName) {
        this.mainGroupName = mainGroupName;
    }

    public long getMainGroupId() {
        return this.mainGroupId;
    }

    public void setMainGroupId(long mainGroupId) {
        this.mainGroupId = mainGroupId;
    }

    public boolean isGlobalBan() {
        return this.globalBan;
    }

    public boolean getGlobalBan() {
        return this.globalBan;
    }

    public void setGlobalBan(boolean globalBan) {
        this.globalBan = globalBan;
    }

    public boolean isGlobalKick() {
        return this.globalKick;
    }

    public boolean getGlobalKick() {
        return this.globalKick;
    }

    public void setGlobalKick(boolean globalKick) {
        this.globalKick = globalKick;
    }

    public boolean isGlobalVerify() {
        return this.globalVerify;
    }

    public boolean getGlobalVerify() {
        return this.globalVerify;
    }

    public void setGlobalVerify(boolean globalVerify) {
        this.globalVerify = globalVerify;
    }

    public boolean isGlobalAntiUnban() {
        return this.globalAntiUnban;
    }

    public boolean getGlobalAntiUnban() {
        return this.globalAntiUnban;
    }

    public void setGlobalAntiUnban(boolean globalAntiUnban) {
        this.globalAntiUnban = globalAntiUnban;
    }

    public boolean isGlobalFilter() {
        return this.globalFilter;
    }

    public boolean getGlobalFilter() {
        return this.globalFilter;
    }

    public void setGlobalFilter(boolean globalFilter) {
        this.globalFilter = globalFilter;
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

    public boolean isGlobalAutomod() {
        return this.globalAutomod;
    }

    public boolean getGlobalAutomod() {
        return this.globalAutomod;
    }

    public void setGlobalAutomod(boolean globalAutomod) {
        this.globalAutomod = globalAutomod;
    }

    public int getMassMention() {
        return this.automodMassMention;
    }

    public void setMassMention(int automodMassMention) {
        this.automodMassMention = automodMassMention;
    }

    public int getEmojiSpam() {
        return this.automodEmojiSpam;
    }

    public void setEmojiSpam(int automodEmojiSpam) {
        this.automodEmojiSpam = automodEmojiSpam;
    }

    public int getLinkSpam() {
        return this.automodLinkSpam;
    }

    public void setLinkSpam(int automodLinkSpam) {
        this.automodLinkSpam = automodLinkSpam;
    }

    public int getMessageSpam() {
        return this.automodMessageSpam;
    }

    public void setMessageSpam(int automodMessageSpam) {
        this.automodMessageSpam = automodMessageSpam;
    }

    public int getImageSpam() {
        return this.automodImageSpam;
    }

    public void setImageSpam(int automodImageSpam) {
        this.automodImageSpam = automodImageSpam;
    }

    public int getCharacterSpam() {
        return this.automodCharacterSpam;
    }

    public void setCharacterSpam(int automodCharacterSpam) {
        this.automodCharacterSpam = automodCharacterSpam;
    }

}
