/*
 * Copyright (c) 2019.
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

package com.pinewoodbuilders.commands.system;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.commands.SystemCommand;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.annotations.DeprecatedSince;
import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.annotations.ReplaceWith;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.*;

@Deprecated
@DeprecatedSince("3.0.0")
@ReplaceWith("ServerSettingsSubCommand")
@ForRemoval(deadline = "3.2.1")
public class RoleSettingsCommand extends SystemCommand {

    private static final Logger log = LoggerFactory.getLogger(RoleSettingsCommand.class);

    public RoleSettingsCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Role Management Command";
    }

    @Override
    public String getDescription() {
        return "This command is used to modify the manager, mod and admin roles in the official pinewood discords.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <role> [status]` - Toggles the locking feature on/off."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command` - Lists all the roles that currently has their XP status disabled."
        );
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("rmanage");
    }

    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.SYSTEM;
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MISCELLANEOUS);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();

        if (guildTransformer == null) {
            context.makeError("Server settings could not be gathered").queue();
            return false;
        }

        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledRoles(context, guildTransformer);
        }

        switch (args[0].toLowerCase()) {
            case "get-level":
                return getUserLevel(context, guildTransformer);
            case "setup-basic-roles":
                return handleFirstSetupRoles(context, guildTransformer);
            case "group-id":
            case "sgi":
            case "set-group-id":
                return runSetGroupId(context, args);
            case "smhrr":
            case "s-mhr-r":
            case "set-mhr-rank":
                return runSetMinimalHrRank(context, args);
            case "smlr":
            case "s-l-r":
            case "set-ml-rank":
                return runSetMinimalLeadRank(context, args);
            case "smr":
            case "set-main-role":
                return runSetMainRole(context, args);
            case "kick-non-mods":
                return kickNonMods(context);
            default:
                return handleRoleSetupArguments(context, args);
        }


    }

    private boolean kickNonMods(CommandMessage context) {
        int level = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (level < GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel()) {
            context.makeError("You got to be MGM or above to run this command.").queue();
            return false;
        }
        List <Member> members = new ArrayList <>();
        int count = 0;
        for (Member m : context.guild.getMembers()) {
            if (!PermissionUtil.canInteract(context.getGuild().getSelfMember(), m)) {
                continue;
            }

            int kickedLevel = XeusPermissionUtil.getPermissionLevel(context.getGuildSettingsTransformer(), context.guild, m).getLevel();
            if (kickedLevel >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
                continue;
            }

            count++;
            members.add(m);
        }


        int finalCount = count;
        context.makeWarning("Would you like to prune `:count` members for archiving the server?").set("count", count).queue(countMessage -> {
            countMessage.addReaction(Emoji.fromFormatted("\uD83D\uDC4D")).queue();
            countMessage.addReaction(Emoji.fromFormatted("\uD83D\uDC4E")).queue();
            avaire.getWaiter().waitForEvent(MessageReactionAddEvent.class, check -> check.getMember().equals(context.member) && check.getMessageId().equals(countMessage.getId()), action -> {

                    switch (action.getEmoji().getName()) {
                        case "\uD83D\uDC4D":
                            for (Member m : members) {
                                m.getUser().openPrivateChannel().queue(k -> k.sendMessage("You have been kicked from `" + context.getGuild().getName() + "` due to it being archived.\n" +
                                    "\n" +
                                    "\n***NOTICE FOR DISCORD STAFF***: ```This is not an advert, this user has been kicked and notified for the kick. We apologise for the inconvenience.```").queue());
                                action.getGuild().kick(m, "Unverified kick - Not in the group.").queue();
                            }
                            context.makeSuccess("`:count` members have been kicked and the server has been archived!").set("count", finalCount).queue();
                            break;
                        case "\uD83D\uDC4E":
                            context.makeInfo("Stopped archive, nothing has happened.").queue();
                    }
                }
            );
        });
        return true;
    }

    private boolean updateMinimalLeadRank(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("minimum_lead_rank", transformer.getMinimumLeadRank());
            });

            context.makeSuccess("Set the minimal lead rank for `:guild`'s configured group (`:groupId`) to ``:id``")
                .set("groupId", transformer.getRobloxGroupId() != 0 ? transformer.getRobloxGroupId() : "ID NOT SET")
                .set("guild", context.getGuild().getName())
                .set("id", transformer.getMinimumLeadRank()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }
    }
    private boolean updateMinimalHrRank(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("minimum_hr_rank", transformer.getMinimumHrRank());
            });

            context.makeSuccess("Set the minimal hr rank for `:guild`'s configured group (`:groupId`) to ``:id``")
                .set("groupId", transformer.getRobloxGroupId() != 0 ? transformer.getRobloxGroupId() : "ID NOT SET")
                .set("guild", context.getGuild().getName())
                .set("id", transformer.getMinimumHrRank());
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }
    }

    private boolean runSetMinimalLeadRank(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setMinimalLeadRank(Integer.parseInt(args[1]));
            return updateMinimalLeadRank(transformer, context);
        } else {
            return sendErrorMessage(context, "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean runSetMinimalHrRank(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setMinimumHrRank(Integer.parseInt(args[1]));
            return updateMinimalHrRank(transformer, context);
        } else {
            return sendErrorMessage(context, "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean runSetGroupId(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setMainGroupId(Integer.parseInt(args[1]));
            return updateGroupId(transformer, context);
        } else {
            return sendErrorMessage(context, "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean runSetMainRole(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setMainDiscordRole(Long.parseLong(args[1]));
            return updateMainRole(transformer, context);
        } else {
            return sendErrorMessage(context, "Something went wrong, please check if you ran the command correctly.");
        }

    }

    private boolean updateMainRole(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("main_discord_role", transformer.getMainDiscordRole());
            });

            context.makeSuccess("Set the main discord role for ``:guild`` to ``:id``").set("guild", context.getGuild().getName()).set("id", transformer.getMainDiscordRole()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }
    }

    private boolean updateGroupId(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("roblox_group_id", transformer.getRobloxGroupId());
            });

            context.makeSuccess("Set the ID for ``:guild`` to ``:id``").set("guild", context.getGuild().getName()).set("id", transformer.getRobloxGroupId()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }
    }

    private boolean handleRoleSetupArguments(CommandMessage context, String[] args) {
        Role role = MentionableUtil.getRole(context.getMessage(), new String[]{args[0]});
        if (role == null) {
            return sendErrorMessage(context, context.i18n("invalidRole", args[0]));
        }


        if (args.length > 1) {
            switch (args[1]) {
                case "mod":
                case "admin":
                case "manager":
                case "no-links":
                case "group-shout":
                    if (args.length > 2) {
                        return handleToggleRole(context, role, args[1], ComparatorUtil.getFuzzyType(args[2]));
                    }
                    return handleToggleRole(context, role, args[1], ComparatorUtil.getFuzzyType(args[1]));

                default:
                    context.makeError("Invalid role given to manage.").queue();
                    return false;
            }

        }
        return handleToggleRole(context, role, "mod", ComparatorUtil.ComparatorType.UNKNOWN);
    }

    private boolean getUserLevel(CommandMessage context, GuildSettingsTransformer guildTransformer) {
        if (context.getMessage().getMentions().getMembers().size() == 1) {
            Member m = context.getMessage().getMentions().getMembers().get(0);
            context.makeInfo(m.getAsMention() + " has permission level ``"
                + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, m).getLevel() +
                "`` and is classified as a **" + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, m).getRankName()
                + "**").queue();
            return true;
        }
        context.makeInfo(context.member.getAsMention() + " has permission level ``" +
            XeusPermissionUtil.getPermissionLevel(context).getLevel() + "`` and is classified as a **" +
            XeusPermissionUtil.getPermissionLevel(context).getRankName() + "**").queue();
        return true;
    }

    private boolean handleFirstSetupRoles(CommandMessage context, GuildSettingsTransformer transformer) {
        Set <Long> admins = transformer.getLeadRoles();
        Set <Long> mods = transformer.getHRRoles();
        Set <Long> managers = transformer.getLeadRoles();

        admins.clear();
        mods.clear();
        managers.clear();

        for (Role r : context.guild.getRoles()) {
            if (r.isManaged()) {
                continue;
            }
            if (r.hasPermission(Permission.ADMINISTRATOR)) {
                admins.add(r.getIdLong());
            }
            if (r.hasPermission(Permission.MANAGE_SERVER) && !r.hasPermission(Permission.ADMINISTRATOR)) {
                managers.add(r.getIdLong());
            }
            if (r.hasPermission(Permission.MESSAGE_MANAGE) && !r.hasPermission(Permission.ADMINISTRATOR) && !r.hasPermission(Permission.MANAGE_SERVER)) {
                mods.add(r.getIdLong());
            }
        }
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                .where("id", context.getGuild().getId())
                .update(statement -> {
                    statement.set("admin_roles", Xeus.gson.toJson(
                        admins
                    ), true);
                    statement.set("manager_roles", Xeus.gson.toJson(
                        managers
                    ), true);
                    statement.set("moderator_roles", Xeus.gson.toJson(
                        mods
                    ), true);
                });
            StringBuilder sb = new StringBuilder();

            runAdminRolesCheck(context, admins.size() > 0, sb, admins);
            runManagerRolesCheck(context, managers.size() > 0, sb, managers);
            runModRolesCheck(context, mods.size() > 0, sb, mods);

            context.makeSuccess(sb.toString() + "\n\nHave been added to the database!").queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong when saving the roles to the database.").queue();
            return false;
        }

    }

    private void runModRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set <Long> mods) {
        if (b) {
            sb.append("\n\n**Moderator roles**:");
            for (Long s : mods) {
                Role r = context.getGuild().getRoleById(s);
                if (r != null) {
                    sb.append("\n - ").append(r.getAsMention());
                }
            }
        } else {
            sb.append("\n\n**Moderator roles**:\n" + "" + "No roles have been found!");
        }
    }

    private void runGroupShoutRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set <Long> groupShouts) {
        if (b) {
            sb.append("\n\n**Group Shout roles**:");
            for (Long s : groupShouts) {
                Role r = context.getGuild().getRoleById(s);
                if (r != null) {
                    sb.append("\n - ").append(r.getAsMention());
                }
            }
        } else {
            sb.append("\n\n**Group Shout roles**:\n" + "" + "No roles have been found!");
        }
    }

    private void runManagerRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set <Long> managers) {
        if (b) {
            sb.append("\n\n**Manager roles**:");
            for (Long s : managers) {
                Role r = context.getGuild().getRoleById(s);
                if (r != null) {
                    sb.append("\n - ").append(r.getAsMention());
                }
            }
        } else {
            sb.append("\n\n**Manager roles**:\n" + "" + "No roles have been found!");
        }
    }

    private void runAdminRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set <Long> admins) {
        if (b) {
            sb.append("\n\n**Admin roles**:");
            for (Long s : admins) {
                Role r = context.getGuild().getRoleById(s);
                if (r != null) {
                    sb.append("\n - ").append(r.getAsMention());
                }
            }
        } else {
            sb.append("\n\n**Admin roles**:\n" + "" + "No roles have been found!");
        }
    }

    private void runNoLinksRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set <Long> admins) {
        if (b) {
            sb.append("\n\n**No-Link roles** (Roles that can't send any links):");
            for (Long s : admins) {
                Role r = context.getGuild().getRoleById(s);
                if (r != null) {
                    sb.append("\n - ").append(r.getAsMention());
                }
            }
        } else {
            sb.append("\n\n**No-Link roles**:\n" + "" + "No roles have been found!");
        }
    }

    private boolean sendEnabledRoles(CommandMessage context, GuildSettingsTransformer transformer) {
        if (transformer.getLeadRoles().isEmpty() && transformer.getHRRoles().isEmpty() && transformer.getLeadRoles().isEmpty() && transformer.getNoLinksRoles().isEmpty()
            && transformer.getMainDiscordRole() == 0 && transformer.getRobloxGroupId() == 0) {
            return sendErrorMessage(context, "Sorry, but there are no manager, admin, mod, main role id, roblox group id or no-links roles on the discord configured.");
        }

        Set <Long> mod = transformer.getHRRoles();
        Set <Long> manager = transformer.getLeadRoles();
        Set <Long> admins = transformer.getLeadRoles();
        Set <Long> noLinks = transformer.getNoLinksRoles();
        Set <Long> groupShouts = transformer.getGroupShoutRoles();
        Long groupId = transformer.getRobloxGroupId();
        Long mainRoleId = transformer.getMainDiscordRole();

        StringBuilder sb = new StringBuilder();
        runAdminRolesCheck(context, admins.size() > 0, sb, admins);
        runManagerRolesCheck(context, manager.size() > 0, sb, manager);
        runModRolesCheck(context, mod.size() > 0, sb, mod);
        runNoLinksRolesCheck(context, noLinks.size() > 0, sb, noLinks);
        runGroupShoutRolesCheck(context, groupShouts.size() > 0, sb, groupShouts);
        runRobloxGroupIdCheck(context, sb, groupId);
        runMainRoleIdCheck(context, sb, mainRoleId);

        context.makeInfo(context.i18n("listRoles"))
            .set("roles", sb.toString())
            .setTitle(context.i18n("listRolesTitle",
                transformer.getHRRoles().size() + transformer.getLeadRoles().size() + transformer.getLeadRoles().size() + transformer.getNoLinksRoles().size()
            ))
            .queue();


        return true;
    }

    private void runRobloxGroupIdCheck(CommandMessage context, StringBuilder sb, Long groupId) {
        if (groupId != 0) {
            sb.append("\n\n**Roblox Group ID**: ``").append(groupId).append("``");
        } else {
            sb.append("\n\n**Roblox Group ID**\n``Group ID has not been set!``");
        }
    }

    private void runMainRoleIdCheck(CommandMessage context, StringBuilder sb, Long mainRoleId) {
        if (mainRoleId != null) {
            sb.append("\n\n**Main Role ID**: ``").append(mainRoleId).append("``");
        } else {
            sb.append("\n\n**Main Role ID**\n``Main Role ID has not been set!``");
        }
    }

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleRole(CommandMessage context, Role role, String rank, ComparatorUtil.ComparatorType value) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();

        switch (value) {
            case FALSE:
                if (rank.equals("admin")) {
                    guildTransformer.getLeadRoles().remove(role.getIdLong());
                }
                if (rank.equals("manager")) {
                    guildTransformer.getLeadRoles().remove(role.getIdLong());
                }
                if (rank.equals("mod")) {
                    guildTransformer.getHRRoles().remove(role.getIdLong());
                }
                if (rank.equals("no-links")) {
                    guildTransformer.getNoLinksRoles().remove(role.getIdLong());
                }
                if (rank.equals("group-shout")) {
                    guildTransformer.getGroupShoutRoles().remove(role.getIdLong());
                }
                break;

            case TRUE:
                if (rank.equals("admin")) {
                    guildTransformer.getLeadRoles().add(role.getIdLong());
                }
                if (rank.equals("manager")) {
                    guildTransformer.getLeadRoles().add(role.getIdLong());
                }
                if (rank.equals("mod")) {
                    guildTransformer.getHRRoles().add(role.getIdLong());
                }
                if (rank.equals("no-links")) {
                    guildTransformer.getNoLinksRoles().add(role.getIdLong());
                }
                if (rank.equals("group-shout")) {
                    guildTransformer.getGroupShoutRoles().add(role.getIdLong());
                }

                break;

            case UNKNOWN:
                if (rank.equals("admin")) {
                    if (guildTransformer.getLeadRoles().contains(role.getIdLong())) {
                        guildTransformer.getLeadRoles().remove(role.getIdLong());
                    } else {
                        guildTransformer.getLeadRoles().add(role.getIdLong());
                    }
                    break;
                }

                if (rank.equals("manager")) {
                    if (guildTransformer.getLeadRoles().contains(role.getIdLong())) {
                        guildTransformer.getLeadRoles().remove(role.getIdLong());
                    } else {
                        guildTransformer.getLeadRoles().add(role.getIdLong());
                    }
                    break;
                }
                if (rank.equals("mod")) {
                    if (guildTransformer.getHRRoles().contains(role.getIdLong())) {
                        guildTransformer.getHRRoles().remove(role.getIdLong());
                    } else {
                        guildTransformer.getHRRoles().add(role.getIdLong());
                    }
                    break;
                }
                if (rank.equals("no-links")) {
                    if (guildTransformer.getNoLinksRoles().contains(role.getIdLong())) {
                        guildTransformer.getNoLinksRoles().remove(role.getIdLong());
                    } else {
                        guildTransformer.getNoLinksRoles().add(role.getIdLong());
                    }
                    break;
                }
                if (rank.equals("group-shout")) {
                    if (guildTransformer.getGroupShoutRoles().contains(role.getIdLong())) {
                        guildTransformer.getGroupShoutRoles().remove(role.getIdLong());
                    } else {
                        guildTransformer.getGroupShoutRoles().add(role.getIdLong());
                    }
                    break;
                }
        }

        boolean isEnabled = guildTransformer.getHRRoles().contains(role.getIdLong()) ||
            guildTransformer.getLeadRoles().contains(role.getIdLong()) ||
            guildTransformer.getLeadRoles().contains(role.getIdLong()) ||
            guildTransformer.getNoLinksRoles().contains(role.getIdLong()) ||
            guildTransformer.getGroupShoutRoles().contains(role.getIdLong());

        try {
            if (rank.equals("admin")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId())
                    .update(statement -> {
                        statement.set("admin_roles", Xeus.gson.toJson(
                            guildTransformer.getLeadRoles()
                        ), true);
                    });
            }
            if (rank.equals("manager")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId())
                    .update(statement -> {
                        statement.set("manager_roles", Xeus.gson.toJson(
                            guildTransformer.getLeadRoles()
                        ), true);
                    });
            }
            if (rank.equals("mod")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId())
                    .update(statement -> {
                        statement.set("moderator_roles", Xeus.gson.toJson(
                            guildTransformer.getHRRoles()
                        ), true);
                    });
            }
            if (rank.equals("no-links")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId())
                    .update(statement -> {
                        statement.set("no_links_roles", Xeus.gson.toJson(
                            guildTransformer.getNoLinksRoles()
                        ), true);
                    });
            }
            if (rank.equals("group-shout")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId())
                    .update(statement -> {
                        statement.set("group_shout_roles", Xeus.gson.toJson(
                            guildTransformer.getGroupShoutRoles()
                        ), true);
                    });
            }

            context.makeSuccess(context.i18n("success"))
                .set("role", role.getAsMention())
                .set("status", context.i18n(isEnabled ? "status.enabled" : "status.disabled"))
                .set("rank", rank)
                .queue();

            return true;
        } catch (SQLException e) {
            log.error("Failed to save the level exempt roles to the database for guild {}, error: {}",
                context.getGuild().getId(), e.getMessage(), e
            );

            context.makeError("Failed to save the changes to the database, please try again. If the issue persists, please contact one of my developers.").queue();

            return false;
        }
    }
}
