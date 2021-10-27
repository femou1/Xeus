package com.pinewoodbuilders.moderation.punishments.globalban;

import com.pinewoodbuilders.database.collection.DataRow;

public class GlobalBanContainer {
    private final String reason;
    private final long robloxId;
    private final String robloxUsername;
    private final long mgi;
    private final String userId;
    private final String punisherId;

    public GlobalBanContainer(DataRow data) {
        userId = data.getString("userId");
        punisherId = data.getString("punisherId");
        mgi = data.getLong("main_group_id");
        reason = data.getString("reason");
        robloxId = data.getLong("roblox_user_id");
        robloxUsername = data.getString("roblox_username");
    }

    public GlobalBanContainer(String reason, long robloxId, String robloxUsername, long mgi, String userId, String punisherId) {
        this.reason = reason;
        this.robloxId = robloxId;
        this.robloxUsername = robloxUsername;
        this.mgi = mgi;
        this.userId = userId;
        this.punisherId = punisherId;
    }

    public String getReason() {
        return reason;
    }

    public long getRobloxId() {
        return robloxId;
    }

    public String getRobloxUsername() {
        return robloxUsername;
    }

    public long getMainGroupId() {
        return mgi;
    }

    public String getUserId() {
        return userId;
    }

    public String getPunisherId() {
        return punisherId;
    }

    public boolean isSame(long mgi, String userId) {
        if (userId == null || this.userId == null) {return false;}
        return this.userId.equals(userId) && this.mgi == mgi;
    }
}
