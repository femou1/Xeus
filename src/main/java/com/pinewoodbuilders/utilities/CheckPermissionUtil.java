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

package com.pinewoodbuilders.utilities;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.sql.SQLException;

public class CheckPermissionUtil {

    /**
     * Checks if the bot can send embed messages in the given message channel.
     *
     * @param channel The message channel that should be checked.
     * @return <code>True</code> if the bot can send a message in it,
     *         <code>False</code> otherwise.
     */
    public static PermissionCheckType canSendMessages(@Nullable MessageChannel channel) {
        if (!(channel instanceof TextChannel)) {
            return PermissionCheckType.EMBED;
        }

        TextChannel textChannel = (TextChannel) channel;
        Member member = textChannel.getGuild().getSelfMember();

        if (member.hasPermission(textChannel, Permission.ADMINISTRATOR)) {
            return PermissionCheckType.EMBED;
        }

        if (!member.hasPermission(textChannel, Permission.MESSAGE_WRITE)) {
            return PermissionCheckType.NONE;
        }

        if (!member.hasPermission(textChannel, Permission.MESSAGE_EMBED_LINKS)) {
            return PermissionCheckType.MESSAGE;
        }

        if (checkForRawEmbedPermission(PermissionUtil.getExplicitPermission(textChannel, member))) {
            return PermissionCheckType.EMBED;
        }

        if (checkForRawEmbedPermission(
                PermissionUtil.getExplicitPermission(textChannel, textChannel.getGuild().getPublicRole()))) {
            return PermissionCheckType.EMBED;
        }

        if (checkForRawEmbedPermission(PermissionUtil.getExplicitPermission(textChannel, member.getRoles().get(0)))) {
            return PermissionCheckType.EMBED;
        }

        return PermissionCheckType.MESSAGE;
    }

    /**
     * Checks if the given permission value includes the raw embed permission value.
     *
     * @param permissions The permission value that should be checked.
     * @return <code>True</code> if the given raw permission value includes the
     *         embed permissions, <code>False</code> otherwise.
     */
    private static boolean checkForRawEmbedPermission(long permissions) {
        for (Permission permission : Permission.getPermissions(permissions)) {
            if (permission.getRawValue() == 0x00004000) {
                return true;
            }
        }
        return false;
    }

    /**
     * The permission check type, the permission type are used to describe what type
     * of permissions the bot has for the current channel.
     */
    public enum PermissionCheckType {

        /**
         * Represents the bot having access to both send, and embed send permissions.
         */
        EMBED(true, true),

        /**
         * Represents the bot having access to send messages, but not to send embed
         * message permissions.
         */
        MESSAGE(true, false),

        /**
         * Represents the bot not having access to send messages in any form.
         */
        NONE(false, false);

        private final boolean canSendMessage;
        private final boolean canSendEmbed;

        PermissionCheckType(boolean canSendMessage, boolean canSendEmbed) {
            this.canSendMessage = canSendMessage;
            this.canSendEmbed = canSendEmbed;
        }

        /**
         * Checks if the current type allows sending normal messages.
         *
         * @return <code>True</code> if the type allows sending normal messages,
         *         <code>False</code> otherwise.
         */
        public boolean canSendMessage() {
            return canSendMessage;
        }

        /**
         * Checks if the current type allows sending embed messages.
         *
         * @return <code>True</code> if the type allows sending embed messages,
         *         <code>False</code> otherwise.
         */
        public boolean canSendEmbed() {
            return canSendEmbed;
        }
    }

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

    public static GuildPermissionCheckType getPermissionLevel(GuildSettingsTransformer guildTransformer, Guild guild, Member member) {
        if (Xeus.getInstance().getBotAdmins().getUserById(member.getUser().getIdLong(), true).isGlobalAdmin()) {
            return GuildPermissionCheckType.BOT_ADMIN;
        }

        if (isGlobalAdmin(member.getId())) {
            return GuildPermissionCheckType.GLOBAL_ADMIN;
        }

        if (guild == null || guildTransformer == null) {
            return GuildPermissionCheckType.USER;
        }

        if (isMainGlobalLeadershipRank(guildTransformer.getMainGroupId(), member.getId())) {
            return GuildPermissionCheckType.MAIN_GLOBAL_LEADERSHIP;
        }

        if (isMainGlobalModRank(guildTransformer.getMainGroupId(), member.getId())) {
            return GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR;
        }


            if (guildTransformer.getLeadRoles() != null) {
                for (Long roleId : guildTransformer.getLeadRoles()) {
                    Role r = guild.getRoleById(roleId);
                    if (r != null) {
                        if (member.getRoles().contains(r)) {
                            return GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP;
                        }
                    }
                }
            }

            if (guildTransformer.getHRRoles() != null) {
                for (Long roleId : guildTransformer.getHRRoles()) {
                    Role r = guild.getRoleById(roleId);
                    if (r != null) {
                        if (member.getRoles().contains(r)) {
                            return GuildPermissionCheckType.LOCAL_GROUP_HR;
                        }
                    }
                }
            }

            if (guildTransformer.getGroupShoutRoles() != null) {
                for (Long roleId : guildTransformer.getGroupShoutRoles()) {
                    Role r = guild.getRoleById(roleId);
                    if (r != null) {
                        if (member.getRoles().contains(r)) {
                            return GuildPermissionCheckType.GROUP_SHOUT;
                        }
                    }
                }
            }
        
        return GuildPermissionCheckType.USER;
    }

    public static boolean isMainGlobalModRank(long groupId, String memberId) {
        if (!Xeus.getInstance().areWeReadyYet()) {return false;}
        
        if (groupId == 0) {
            return false;
        }

        VerificationEntity entity = Xeus.getInstance().getRobloxAPIManager().getVerification().fetchVerificationFromDatabase(memberId, true);
        if (entity == null) {
            return false;
        }


        QueryBuilder builder = Xeus.getInstance().getDatabase()
            .newQueryBuilder(Constants.GROUP_MODERATORS_TABLE)
            .where("main_group_id", groupId)
            .andWhere("discord_id", memberId)
            .andWhere("roblox_id", entity.getRobloxId());
        
        try {
            Collection coll = builder.get();
            if (coll.size() > 0) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
        
        return false;
    }

    public static boolean isMainGlobalLeadershipRank(long groupId, String memberId) {
        if (!Xeus.getInstance().areWeReadyYet()) {return false;}
        
        if (groupId == 0) {
            return false;
        }

        VerificationEntity entity = Xeus.getInstance().getRobloxAPIManager().getVerification().fetchVerificationFromDatabase(memberId, true);
        if (entity == null) {
            return false;
        }


        QueryBuilder builder = Xeus.getInstance().getDatabase()
        .newQueryBuilder(Constants.GROUP_MODERATORS_TABLE)
        .where("main_group_id", groupId)
        .andWhere("discord_id", memberId)
        .andWhere("roblox_id", entity.getRobloxId())
        .andWhere("is_global_lead", 1);
        
        try {
            Collection coll = builder.get();
            if (coll.size() > 0) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
        
        return false;
    }

    public static boolean isGlobalAdmin(String memberId) {
        if (!Xeus.getInstance().areWeReadyYet()) {return false;}

        VerificationEntity entity = Xeus.getInstance().getRobloxAPIManager().getVerification().fetchVerificationFromDatabase(memberId, true);
        if (entity == null) {
            return false;
        }

        return isFullGlobalAdmin(entity.getDiscordId(), entity.getRobloxId());
    }

    public static boolean isFullGlobalAdmin(Long discordId, long robloxId) {
        QueryBuilder builder = Xeus.getInstance().getDatabase()
        .newQueryBuilder(Constants.GROUP_MODERATORS_TABLE)
        .andWhere("main_group_id", 0)
        .andWhere("discord_id", discordId)
        .andWhere("roblox_id", robloxId)
        .andWhere("is_global_admin", 1);
        
        try {
            Collection coll = builder.get();
            if (coll.size() > 0) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
        
        return false;
    }

    public static GuildPermissionCheckType getPermissionLevel(@NotNull CommandContext context) {
        return getPermissionLevel(context.getGuildSettingsTransformer(), context.getGuild(), context.getMember());
    }
}
