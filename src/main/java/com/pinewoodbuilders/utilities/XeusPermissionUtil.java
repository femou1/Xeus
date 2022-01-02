package com.pinewoodbuilders.utilities;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class XeusPermissionUtil {


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
