package com.pinewoodbuilders.contracts.permission;

/**
 * The list of permissions the users can get based on their group permissions, these can only be set by a rank above you.
 * Except GLOBAL_ADMIN, this has to be voted on by all major guilds that use this bot.
 *
 */
public enum GuildPermissionCheckType {
    BOT_ADMIN(100, "Bot Developer (Global)", "This permission level gives access to everything regarding the bot. However, the guilds need to allow this with the 'bypass' setting. This only applies to this specific role."),
    GLOBAL_ADMIN(95, "Global Admin", "This person has the ability to moderate on other guilds. This permission will only be given if all special groups vote for the mod. Otherwise, the MGM permission is applied."),
    MAIN_GLOBAL_LEADERSHIP(90, "Main Group Leadership (Within :groupId)", "This is the leadership of any group, this depends on the guilds you are in. They have the ability to vote on votes, and are able to assign the HR roles of their own group."),
    MAIN_GLOBAL_MODERATOR(80, "Main Group Moderators (Within :groupId)", "These are the HR's of a main group, they moderate all discords and can global ban + do everything not group specific on all connected guilds."),
    LOCAL_GROUP_LEADERSHIP(70, "Admin / Division Leader / Local Admin (Within :localGroupId)", "Bosses of divisional groups. Can assign LGH's"),
    LOCAL_GROUP_HR(20, "HR / Local Mod (Within :localGroupId)", "The moderation of a group, this is the lowest rank with moderation permissions"),
    GROUP_SHOUT(10, "Group Shout Permission", "Permission to use `!gs`"),
    USER(0, "Regular User", "This is anyone without a permission.");

    private final int permissionLevel;
    private final String rankName;
    private final String description;

    GuildPermissionCheckType(Integer pL, String rankName, String description) {
        this.permissionLevel = pL;
        this.rankName = rankName;
        this.description = description;
    }
    //
    public int getLevel() {
        return permissionLevel;
    }

    public String getRankName() {
        return rankName;
    }

    public String getDescription() {
        return description;
    }


}
