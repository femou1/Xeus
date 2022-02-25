package com.pinewoodbuilders.moderation.global.punishments.globalban;

import com.pinewoodbuilders.database.collection.DataRow;
import net.dv8tion.jda.annotations.ReplaceWith;

public class GlobalBanContainer {
    private final String reason;
    private final long robloxId;
    private final String robloxUsername;
    private final long mgi;
    private final String userId;
    private final String punisherId;

    public GlobalBanContainer(DataRow data) {
        this.userId = data.getString("userId");
        this.punisherId = data.getString("punisherId");
        this.mgi = data.getLong("main_group_id");
        this.reason = data.getString("reason");
        this.robloxId = data.getLong("roblox_user_id");
        this.robloxUsername = data.getString("roblox_username");
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

    @Deprecated
    @ReplaceWith("GlobalBanContainer#isRobloxSame")
    public boolean isSame(long mgi, String userId) {
        if (userId == null || this.userId == null) {return false;}
        return this.userId.equals(userId) && this.mgi == mgi;
    }

    public boolean isRobloxSame(long mgi, long robloxId) {
        if (this.robloxId == 0 || robloxId == 0) {return false;}
        return (this.robloxId == robloxId && this.mgi == mgi);
    }
}
