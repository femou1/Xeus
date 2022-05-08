package com.pinewoodbuilders.commands.settings.server;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.contracts.commands.settings.SettingsSubCommand;
import com.pinewoodbuilders.contracts.moderation.LinkLevel;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.moderation.global.filter.filter.LinkContainer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import com.pinewoodbuilders.utilities.menu.Paginator;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;

import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ServerSettingsSubCommand extends SettingsSubCommand {
    private final Paginator.Builder builder;

    public ServerSettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        super(avaire, command);
        builder = new Paginator.Builder()
            .setColumns(3)
            .setFinalAction(m -> {try {m.clearReactions().queue();} catch (PermissionException ignore) {}})
            .setItemsPerPage(50)
            .waitOnSinglePage(false)
            .useNumberedItems(false)
            .showPageNumbers(true)
            .wrapPageEnds(true)
            .setEventWaiter(avaire.getWaiter())
            .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();

        int permission = XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, context.member).getLevel();
        if (permission < GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
            return command.sendErrorMessage(context, "Sorry, but you do not have the permissions required to run this command.");
        }

        if (guildTransformer == null) {
            context.makeError("Server settings could not be gathered").queue();
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "link-filter", "links", "l", "lf" -> runLinkFilter(context, guildTransformer, Arrays.copyOfRange(args, 1, args.length));
            case "smhrr", "s-mhr-r", "set-mhr-rank" -> runSetMinimalHrRank(context, args);
            case "smlr", "s-l-r", "set-ml-rank" -> runSetMinimalLeadRank(context, args);
            case "main-group-id", "smgi", "set-main-group-id" -> runSetMainGroupId(context, args);
            case "permissions", "modify-permissions", "roles", "setup" -> handleRoleSetupArguments(context, Arrays.copyOfRange(args, 1, args.length));
            case "group-id", "sgi", "set-group-id" -> runSetGroupId(context, args);
            case "uac", "user-alerts-channel", "alerts-channel" -> runYoungWarningChannelUpdateCommand(context, Arrays.copyOfRange(args, 1, args.length), guildTransformer);
            case "young-warning-channel" -> runYoungWarningChannelUpdateCommand(context, args, context.getGuildSettingsTransformer());
            case "audit-ignored-channels", "aic", "ignore-audit" -> onFilterChannelCommand(context, Arrays.copyOfRange(args, 1, args.length));
            default -> command.sendErrorMessage(context, "I'm unable to find the argument you're looking for, ya twat. Try again. Lemme remind you of the possible commands.", 5, TimeUnit.MINUTES);
        };
    }



    private boolean runLinkFilter(CommandMessage context, GuildSettingsTransformer guildTransformer, String[] args) {
        if (guildTransformer.getMainGroupId() == 0) {
            return command.sendErrorMessage(context, "Main group ID is not set, this feature is disabled.");
        }

        if (!guildTransformer.isOfficialSubGroup()) {
            return command.sendErrorMessage(context, "You are not an official subgroup or subgroup server, you are not allowed to use this command because of that.");
        }

        if (args.length == 0) {
            return command.sendErrorMessage(context, """
                I'm unable to find the argument you're looking for, ya twat. Try again. Lemme remind you of the possible commands:
                 - `add <domain> <level>`: **Add a domain to the filter**
                 - `remove <domain>`: **Remove a domain to the filter**
                 - `list`: **See all domains and their level**.
                """, 5, TimeUnit.MINUTES);
        }


        return switch (args[0].toLowerCase()) {
            case "add", "a" -> linkFilterAdd(context, guildTransformer, Arrays.copyOfRange(args, 1, args.length));
            case "remove", "r" -> linkFilterRemove(context, guildTransformer, Arrays.copyOfRange(args, 1, args.length));
            case "list", "l" -> linkFilterList(context, guildTransformer, Arrays.copyOfRange(args, 1, args.length));
            case "set-channel", "sc" -> setLinkFilterLogChannel(context, guildTransformer, Arrays.copyOfRange(args, 1, args.length));
            default -> command.sendErrorMessage(context, """
                I'm unable to find the argument you're looking for, ya twat. Try again. Lemme remind you of the possible commands:
                 - `add <domain> <level>`: **Add a domain to the filter**
                 - `remove <domain>`: **Remove a domain to the filter**
                 - `list`: **See all domains and their level**.
                """, 5, TimeUnit.MINUTES);
        };
    }

    private boolean setLinkFilterLogChannel(CommandMessage context, GuildSettingsTransformer guildTransformer, String[] args) {
        if (args.length == 0) {
            return command.sendErrorMessage(context, "Uhhh, I'm missing the channel or ID you want to set the channel on.");
        }

        if (args[0].equals("remove") || args[0].equals("0")) {
            try {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId())
                    .update(statement -> {statement.set("link_filter_log", 0);});
                context.makeSuccess("Disabled the link filter ***log***.").queue();

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return true;
        }

        GuildChannel gc = MentionableUtil.getChannel(context.getMessage(), Arrays.copyOfRange(args, 0, args.length));
        if (gc == null) {
            return command.sendErrorMessage(context, "Channel does not exist.");
        }

        guildTransformer.setLinkFilterLog(gc.getIdLong());

        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId())
                .update(statement -> {statement.set("link_filter_log", gc.getIdLong());});
            context.makeSuccess("Updated link filter channel to " + gc.getAsMention()).queue();
        } catch (SQLException e) {

            e.printStackTrace();
            return command.sendErrorMessage(context, "Something went wrong when modifying the database record.");
        }
        return true;
    }

    private boolean linkFilterList(CommandMessage context, GuildSettingsTransformer guildTransformer, String[] args) {
        HashSet <LinkContainer> s = avaire.getLinkFilterManager().getLinks().get(guildTransformer.getMainGroupId());

        if (s != null) {
            for (LinkContainer linkContainer : s) {
                LinkLevel level = LinkLevel.getLinkLevelFromId(linkContainer.getAction());
                builder.addItems(linkContainer.getTopLevelDomain() + " (`" + level.name() + "`)");
            }

            builder.setText("Current whitelisted links").setColor(new Color(119, 255, 138, 161));
            builder.build().display(context.getChannel());
        }
        return false;
    }

    private boolean linkFilterRemove(CommandMessage context, GuildSettingsTransformer guildTransformer, String[] args) {
        if (args.length == 0) {
            return command.sendErrorMessage(context, "Uhhh, I'm missing the domain in the link. Please, give me the link.");
        }

        if (!avaire.getLinkFilterManager().hasLink(guildTransformer.getMainGroupId(), args[0])) {
            return command.sendErrorMessage(context, "Link doesn't exist in the database. Hence, I cannot remove it.");
        }

        try {
            avaire.getLinkFilterManager().removeLink(guildTransformer.getMainGroupId(), args[0]);
            context.makeSuccess("Removed `" + args[0] + "` from the link filter database of `" + guildTransformer.getMainGroupId() + "`").queue();
        } catch (SQLException e) {
            e.printStackTrace();
            return command.sendErrorMessage(context, "Something went wrong when removing this link from the database.");
        }

        return true;
    }

    private boolean linkFilterAdd(CommandMessage context, GuildSettingsTransformer guildTransformer, String[] args) {
        if (args.length == 0) {
            return command.sendErrorMessage(context, "Uhhh, I'm missing the domain in the link. Please, give me the link.");
        }
        if (args.length == 1 || !NumberUtil.isNumeric(args[1])) {
            StringBuilder message = new StringBuilder();
            for (LinkLevel level : LinkLevel.values()) {
                message.append(" - `").append(level.getLevel()).append("` (**").append(level.name()).append("**)\n");
            }
            return command.sendErrorMessage(context, "Do you have the action ID for me? This way I know what to do once I detect a link.\n\n" + message);
        }

        LinkLevel level = LinkLevel.getLinkLevelFromId(Integer.parseInt(args[1]));
        if (level == LinkLevel.DELETE) {
            context.makeError("Link will already get deleted by default, link has not been added to the database. Make sure you choose the correct ID, since this command will revert to DELETE if it didn't get a valid LinkLevel.").queue();
            return false;
        }

        if (avaire.getLinkFilterManager().hasLink(guildTransformer.getMainGroupId(), args[0])) {
            context.makeInfo("Link already exists in database, overwriting link.").queue();
        }

        try {
            avaire.getLinkFilterManager().registerLink(guildTransformer.getMainGroupId(), args[0], level.getLevel());
            context.makeSuccess("Added `" + args[0] + "` to the link filter database of `" + guildTransformer.getMainGroupId() + "` with level: `" + level.getLevel() + "`").setColor(level.getColor()).queue();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return command.sendErrorMessage(context, "Something went wrong when adding this link to the database.");
        }

    }

    private boolean runYoungWarningChannelUpdateCommand(CommandMessage context, String[] args, GuildSettingsTransformer transformer) {
        TextChannel c = context.getGuild().getTextChannelById(args[0]);
        if (NumberUtil.isNumeric(args[0]) && c != null) {
            context.makeInfo("Updated young member warning channel to " + c.getName()).queue();
            transformer.setUserAlertsChannelId(Long.parseLong(args[0]));
            return updateLocalRecordInDatabase(context, transformer.getUserAlertsChannelId());
        } else {
            context.makeError("Please enter a valid channel ID.").queue();
            return false;
        }
    }

    private boolean updateLocalRecordInDatabase(CommandMessage context,
                                                long memberToYoungChannelId) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                .where("id", context.getGuild().getId()).update(p -> {
                    p.set("user_alerts_channel_id", memberToYoungChannelId);
                });
            context.makeSuccess("Channel was set!").queue();
        } catch (SQLException exception) {
            Xeus.getLogger().error("ERROR: ", exception);
            context.makeError("Something went wrong...");
            return false;
        }
        context.makeSuccess("Done!").queue();
        return true;
    }

    private boolean runSetMinimalLeadRank(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return command.sendErrorMessage(context, "Incorrect arguments");
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
            return command.sendErrorMessage(context,
                "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean sendEnabledRoles(CommandMessage context, GuildSettingsTransformer transformer) {
        Set <Long> mod = transformer.getHRRoles();
        Set <Long> admins = transformer.getLeadRoles();
        Set <Long> noLinks = transformer.getNoLinksRoles();
        Set <Long> groupShouts = transformer.getGroupShoutRoles();
        Long groupId = transformer.getRobloxGroupId();
        Long mainRoleId = transformer.getMainDiscordRole();

        StringBuilder sb = new StringBuilder();
        runAdminRolesCheck(context, admins.size() > 0, sb, admins);
        runModRolesCheck(context, mod.size() > 0, sb, mod);
        runNoLinksRolesCheck(context, noLinks.size() > 0, sb, noLinks);
        runGroupShoutRolesCheck(context, groupShouts.size() > 0, sb, groupShouts);
        runRobloxGroupIdCheck(context, sb, groupId);
        runMainRoleIdCheck(context, sb, mainRoleId);

        context.makeInfo(context.i18n("listRoles")).set("roles", sb.toString())
            .setTitle(
                context.i18n("listRolesTitle",
                    transformer.getHRRoles().size() + transformer.getLeadRoles().size()
                        + transformer.getLeadRoles().size() + transformer.getNoLinksRoles().size()))
            .queue();

        return true;
    }

    private boolean runSetMinimalHrRank(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return command.sendErrorMessage(context, "Incorrect arguments");
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
            return command.sendErrorMessage(context,
                "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean runSetGroupId(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return command.sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setRobloxGroupId(Integer.parseInt(args[1]));
            return updateGroupId(transformer, context);
        } else {
            return command.sendErrorMessage(context,
                "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean handleFirstSetupRoles(CommandMessage context, GuildSettingsTransformer transformer) {
        Set <Long> admins = transformer.getLeadRoles();
        Set <Long> mods = transformer.getHRRoles();

        admins.clear();
        mods.clear();

        for (Role r : context.guild.getRoles()) {
            if (r.isManaged()) {
                continue;
            }
            if (r.hasPermission(Permission.ADMINISTRATOR)) {
                admins.add(r.getIdLong());
            }
            if (r.hasPermission(Permission.MESSAGE_MANAGE) && !r.hasPermission(Permission.ADMINISTRATOR)
                && !r.hasPermission(Permission.MANAGE_SERVER)) {
                mods.add(r.getIdLong());
            }
        }
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId())
                .update(statement -> {
                    statement.set("admin_roles", Xeus.gson.toJson(admins), true);
                    statement.set("moderator_roles", Xeus.gson.toJson(mods), true);
                });
            StringBuilder sb = new StringBuilder();

            runAdminRolesCheck(context, admins.size() > 0, sb, admins);
            runModRolesCheck(context, mods.size() > 0, sb, mods);

            context.makeSuccess(sb.toString() + "\n\nHave been added to the database!").queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong when saving the roles to the database.").queue();
            return false;
        }

    }

    private boolean updateMinimalLeadRank(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id",
            context.guild.getId());
        try {
            qb.update(q -> {
                q.set("minimum_lead_rank", transformer.getMinimumHrRank());
            });

            context.makeSuccess("Set the minimal lead rank for `:guild`'s configured group (`:groupId`) to ``:id``")
                .set("groupId", transformer.getRobloxGroupId() != 0 ? transformer.getRobloxGroupId() : "ID NOT SET")
                .set("guild", context.getGuild().getName()).set("id", transformer.getMinimumLeadRank()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)")
                .queue();
            return false;
        }
    }

    private boolean updateMinimalHrRank(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id",
            context.guild.getId());
        try {
            qb.update(q -> {
                q.set("minimum_hr_rank", transformer.getMinimumHrRank());
            });

            context.makeSuccess("Set the minimal hr rank for `:guild`'s configured group (`:groupId`) to ``:id``")
                .set("groupId", transformer.getRobloxGroupId() != 0 ? transformer.getRobloxGroupId() : "ID NOT SET")
                .set("guild", context.getGuild().getName()).set("id", transformer.getMinimumHrRank())
                .queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)")
                .queue();
            return false;
        }
    }

    private boolean updateGroupId(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id",
            context.guild.getId());
        try {
            qb.update(q -> {
                q.set("roblox_group_id", transformer.getRobloxGroupId());
            });

            context.makeSuccess("Set the ID for ``:guild`` to ``:id``").set("guild", context.getGuild().getName())
                .set("id", transformer.getRobloxGroupId()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)")
                .queue();
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

    private boolean handleRoleSetupArguments(CommandMessage context, String[] args) {

        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledRoles(context, context.getGuildSettingsTransformer());
        }

        switch (args[0]) {
            case "setup-basic-roles":
                return handleFirstSetupRoles(context, context.getGuildSettingsTransformer());
            case "smr":
            case "set-main-role":
                return runSetMainRole(context, args);
            default:
                return handleRoles(context, args);
        }

    }

    private boolean handleRoles(CommandMessage context, String[] args) {
        Role role = MentionableUtil.getRole(context.getMessage(), new String[]{args[0]});
        if (role == null) {
            return command.sendErrorMessage(context, context.i18n("invalidRole", args[0]));
        }

        if (args.length > 1) {
            switch (args[1]) {
                case "mod":
                case "admin":
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

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleRole(CommandMessage context, Role role, String rank,
                                     ComparatorUtil.ComparatorType value) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();

        switch (value) {
            case FALSE:
                if (rank.equals("admin")) {
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

        boolean isEnabled = guildTransformer.getHRRoles().contains(role.getIdLong())
            || guildTransformer.getLeadRoles().contains(role.getIdLong())
            || guildTransformer.getLeadRoles().contains(role.getIdLong())
            || guildTransformer.getNoLinksRoles().contains(role.getIdLong())
            || guildTransformer.getGroupShoutRoles().contains(role.getIdLong());

        try {
            if (rank.equals("admin")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId()).update(statement -> {
                        statement.set("admin_roles", Xeus.gson.toJson(guildTransformer.getLeadRoles()), true);
                    });
            }
            if (rank.equals("manager")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId()).update(statement -> {
                        statement.set("manager_roles", Xeus.gson.toJson(guildTransformer.getLeadRoles()), true);
                    });
            }
            if (rank.equals("mod")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId()).update(statement -> {
                        statement.set("moderator_roles", Xeus.gson.toJson(guildTransformer.getHRRoles()), true);
                    });
            }
            if (rank.equals("no-links")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId()).update(statement -> {
                        statement.set("no_links_roles", Xeus.gson.toJson(guildTransformer.getNoLinksRoles()), true);
                    });
            }
            if (rank.equals("group-shout")) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId()).update(statement -> {
                        statement.set("group_shout_roles", Xeus.gson.toJson(guildTransformer.getGroupShoutRoles()),
                            true);
                    });
            }

            context.makeSuccess(context.i18n("success")).set("role", role.getAsMention())
                .set("status", context.i18n(isEnabled ? "status.enabled" : "status.disabled")).set("rank", rank)
                .queue();

            return true;
        } catch (SQLException e) {
            // log.error("Failed to save the level exempt roles to the database for guild {}, error: {}",
            //         context.getGuild().getId(), e.getMessage(), e);

            context.makeError(
                    "Failed to save the changes to the database, please try again. If the issue persists, please contact one of my developers.")
                .queue();

            return false;
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


    private boolean runSetMainGroupId(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return command.sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setMainGroupId(Integer.parseInt(args[1]));
            return updateMainGroupId(transformer, context);
        } else {
            return command.sendErrorMessage(context,
                "Something went wrong, please check if you ran the command correctly.");
        }
    }

    private boolean runSetMainRole(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return command.sendErrorMessage(context, "Incorrect arguments");
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
            return command.sendErrorMessage(context,
                "Something went wrong, please check if you ran the command correctly.");
        }

    }

    private boolean updateMainRole(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id",
            context.guild.getId());
        try {
            qb.update(q -> {
                q.set("main_discord_role", transformer.getMainDiscordRole());
            });

            context.makeSuccess("Set the main discord role for ``:guild`` to ``:id``")
                .set("guild", context.getGuild().getName()).set("id", transformer.getMainDiscordRole()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)")
                .queue();
            return false;
        }
    }

    private boolean updateMainGroupId(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id",
            context.guild.getId());
        try {
            qb.update(q -> {
                q.set("main_group_id", transformer.getMainGroupId());
            });

            context.makeSuccess("Set the ID for ``:guild`` to ``:id``").set("guild", context.getGuild().getName())
                .set("id", transformer.getMainGroupId()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)")
                .queue();
            return false;
        }
    }

    public boolean onFilterChannelCommand(CommandMessage context, String[] args) {
        GuildTransformer guildTransformer = context.getGuildTransformer();
        if (guildTransformer == null) {
            context.makeError("Server settings could not be gathered").queue();
            return false;
        }

        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledChannels(context, guildTransformer);
        }

        GuildChannel channel = MentionableUtil.getChannel(context.getMessage(), args);
        if (!(channel instanceof TextChannel textChannel)) {
            return command.sendErrorMessage(context, "You must provide a valid text channel.");
        }

        if (!textChannel.canTalk()) {
            return command.sendErrorMessage(context, context.i18n("I can't talk in the {0} channel",
                textChannel.getAsMention()
            ));
        }

        if (args.length > 1) {
            return handleToggleChannel(context, textChannel, ComparatorUtil.getFuzzyType(args[1]));
        }
        return handleToggleChannel(context, textChannel, ComparatorUtil.ComparatorType.UNKNOWN);
    }

    private boolean sendEnabledChannels(CommandMessage context, GuildTransformer guildTransformer) {
        if (guildTransformer.getIgnoredAuditLogChannels().isEmpty()) {
            return command.sendErrorMessage(context, "There are currently no channels with the anti audit log, you can add one by using the `"+command.generateCommandTrigger(context.getMessage())+" s aic <channel>` ");
        }

        List <String> channels = new ArrayList <>();
        for (Long channelId : guildTransformer.getIgnoredAuditLogChannels()) {
            TextChannel textChannel = context.getGuild().getTextChannelById(channelId);
            if (textChannel != null) {
                channels.add(textChannel.getAsMention());
            }
        }

        context.makeInfo("All the channels mentioned below currently will not show anything in audit logs, any channel not on this list is unaffected.\n\n:channels")
            .set("channels", String.join(", ", channels))
            .setTitle("Channels that don't send to audit logs. ("+guildTransformer.getIgnoredAuditLogChannels().size()+")")
            .queue();

        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleChannel(CommandMessage context, TextChannel channel, ComparatorUtil.ComparatorType value) {
        GuildTransformer guildTransformer = context.getGuildTransformer();

        switch (value) {
            case FALSE:
                guildTransformer.getIgnoredAuditLogChannels().remove(channel.getIdLong());
                break;

            case TRUE:
                guildTransformer.getIgnoredAuditLogChannels().add(channel.getIdLong());
                break;

            case UNKNOWN:
                if (guildTransformer.getIgnoredAuditLogChannels().contains(channel.getIdLong()))
                    guildTransformer.getIgnoredAuditLogChannels().remove(channel.getIdLong());
                else
                    guildTransformer.getIgnoredAuditLogChannels().add(channel.getIdLong());
                break;
        }

        boolean isEnabled = guildTransformer.getIgnoredAuditLogChannels().contains(channel.getIdLong());

        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("ignored_audit_log_channels", Xeus.gson.toJson(
                    guildTransformer.getIgnoredAuditLogChannels()
                ), true));

            context.makeSuccess("All audit events have been **:status** for :channel!")
                .set("channel", channel.getAsMention())
                .set("status", isEnabled ? "Disabled" : "Enabled")
                .queue();

            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("Failed to save the level exempt channels to the data for guild {}, error: {}",
                context.getGuild().getId(), e.getMessage(), e
            );

            context.makeError("Failed to save the changes to the database, please try again. If the issue persists, please contact one of my developers.").queue();

            return false;
        }
    }
}
