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

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.database.transformers.Transformer;
import com.pinewoodbuilders.database.collection.DataRow;
import net.dv8tion.jda.api.entities.Guild;

public class VerificationTransformer extends Transformer {

    private String id;
    private String name;
    private String nameRaw;
    private String nicknameFormat;
    private String welcomeMessage;

    private boolean joinDM = true;
    private boolean nicknameUsers = true;
    private long unverifiedRole = 0;
    private long verifiedRole = 0;
    private long announceChannel = 0;
    private long verifyChannel = 0;
    private long bypassRole = 0;

    private String grs;

    public VerificationTransformer(Guild guild) {
        super(null);

        id = guild.getId();
        name = guild.getName();
        nameRaw = guild.getName();
    }

    public VerificationTransformer(Guild guild, DataRow data) {
        super(data);

        if (hasData()) {
            id = data.getString("id");
            name = data.getString("name");
            nameRaw = data.get("name").toString();

            nicknameFormat = data.getString("nickname_format", ":username");
            welcomeMessage = data.getString("welcome_message", "Welcome to :server, :username!");

            joinDM = data.getBoolean("join_dm");
            nicknameUsers = data.getBoolean("nickname_users");

            unverifiedRole = data.getLong("unverified_role");
            verifiedRole = data.getLong("verified_role");
            announceChannel = data.getLong("announce_channel");
            verifyChannel = data.getLong("verify_channel");

            bypassRole = data.getLong("bypass_role");

            if (data.getString("ranks", null) != null) {
                grs = data.getString("ranks");
            }

            reset();
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameRaw() {
        return nameRaw;
    }

    public String getNicknameFormat() {
        return nicknameFormat;
    }

    public void setNicknameFormat(String nicknameFormat) {
        this.nicknameFormat = nicknameFormat;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public boolean isJoinDM() {
        return joinDM;
    }

    public void setJoinDM(boolean joinDM) {
        this.joinDM = joinDM;
    }

    public boolean isNicknameUsers() {
        return nicknameUsers;
    }

    public void setNicknameUsers(boolean nicknameUsers) {
        this.nicknameUsers = nicknameUsers;
    }

    public long getUnverifiedRole() {
        return unverifiedRole;
    }

    public void setUnverifiedRole(long unverifiedRole) {
        this.unverifiedRole = unverifiedRole;
    }

    public long getVerifyChannel() {
        return verifyChannel;
    }

    public void setVerifyChannel(long verifyChannel) {
        this.verifyChannel = verifyChannel;
    }

    public long getBypassRoleId() {
        return bypassRole;
    }

    public void setBypassRoleId(long bypassRole) {
        this.bypassRole = bypassRole;
    }

    public long getAnnounceChannel() {
        return announceChannel;
    }

    public void setAnnounceChannel(long announceChannel) {
        this.announceChannel = announceChannel;
    }

    public long getVerifiedRole() {
        return verifiedRole;
    }

    public void setVerifiedRole(long verifiedRole) {
        this.verifiedRole = verifiedRole;
    }

    public String getRanks() {
        return grs;
    }

    public Object toService(String json, Class<?> clazz) {
        return Xeus.gson.fromJson(json, clazz);
    }

}
