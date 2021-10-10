package com.pinewoodbuilders.commands.settings.global;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.contracts.commands.settings.SettingsSubCommand;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

public class GlobalSettingsSubCommand extends SettingsSubCommand {

    public GlobalSettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        super(avaire, command);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();
        if (!(context.getMember().hasPermission(Permission.ADMINISTRATOR)
                || context.getMember().hasPermission(Permission.MANAGE_SERVER)))

            if (guildTransformer == null) {
                context.makeError("Server settings could not be gathered").queue();
                return false;
            }

        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledRoles(context, guildTransformer);
        }

        switch (args[0].toLowerCase()) {
            case "main-group-id":
            case "smgi":
            case "set-main-group-id":
                return runSetMainGroupId(context, args);
            case "smr":
            case "set-main-role":
                return runSetMainRole(context, args);
            case "permissions":
            case "modify-permissions":
                return handleRoleSetupArguments(context, Arrays.copyOfRange(args, 1, args.length));

            case "audit-logs":
                return runAuditLogsCommand(context, Arrays.copyOfRange(args, 1, args.length), guildTransformer);
            default:
                context.makeInfo(
                        "God damn, please get this command right.\n\n- `smgi` - Set the Main Group Id of this guild")
                        .queue();
                return false;
        }

    }

    private boolean runAuditLogsCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer settingsTransformer) {
        GlobalSettingsTransformer transformer = context.getGlobalSettingsTransformer();

        if (settingsTransformer.getMainGroupId() == 0 || transformer == null) {
            context.makeError(
                    "The global settings connected to this guild could not be loaded, please try again later. If this issue still persists, please contact the developer. In most cases, this guild does not have a main group ID, ask a global mod to set this!")
                    .queue();
            return false;
        }

        if (args.length == 0) {
            context.makeInfo("Please select what setting you'd like to modify (0 = Disabled)\n"
                    + " - ``mass-mention`` - "
                    + (transformer.getMassMention() != 0 ? transformer.getMassMention() : "**Empty/Disabled**") + ")"
                    + "\n" + " - ``emoji-spam`` - "
                    + (transformer.getEmojiSpam() != 0 ? transformer.getEmojiSpam() : "**Empty/Disabled**") + ")" + "\n"
                    + " - ``link-spam`` - "
                    + (transformer.getLinkSpam() != 0 ? transformer.getLinkSpam() : "**Empty/Disabled**") + ")" + "\n"
                    + " - ``message-spam`` - "
                    + (transformer.getMessageSpam() != 0 ? transformer.getMessageSpam() : "**Empty/Disabled**") + ")"
                    + "\n" + " - ``image-spam`` - "
                    + (transformer.getImageSpam() != 0 ? transformer.getImageSpam() : "**Empty/Disabled**") + ")" + "\n"
                    + " - ``character-spam`` - "
                    + (transformer.getCharacterSpam() != 0 ? transformer.getCharacterSpam() : "**Empty/Disabled**")
                    + ")").queue();
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "mass-mention":
                return runMentionUpdateCommand(context, args, transformer);
            case "emoji-spam":
                return runEmojiSpamUpdateCommand(context, args, transformer);
            case "link-spam":
                return runLinkSpamUpdateCommand(context, args, transformer);
            case "message-spam":
                return runMessageSpamUpdateCommand(context, args, transformer);
            case "image-spam":
                return runImageSpamUpdateCommand(context, args, transformer);
            case "character-spam":
                return runCharacterSpamUpdateCommand(context, args, transformer);
        }

        return true;
    }

    private boolean runCharacterSpamUpdateCommand(CommandMessage context, String[] args,
            GlobalSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update character spam to " + args[1]).queue();
            transformer.setCharacterSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_character_spam", transformer.getCharacterSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }

    }

    private boolean runImageSpamUpdateCommand(CommandMessage context, String[] args,
            GlobalSettingsTransformer transformer) {
        context.makeInfo("Update image spam to " + args[1]).queue();
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update link spam to " + args[1]).queue();
            transformer.setImageSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_image_spam", transformer.getImageSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runMessageSpamUpdateCommand(CommandMessage context, String[] args,
            GlobalSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update message spam to " + args[1]).queue();
            transformer.setMessageSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_message_spam", transformer.getMessageSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runLinkSpamUpdateCommand(CommandMessage context, String[] args,
            GlobalSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update link spam to " + args[1]).queue();
            transformer.setLinkSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_link_spam", transformer.getLinkSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runEmojiSpamUpdateCommand(CommandMessage context, String[] args,
            GlobalSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update emoji spam to " + args[1]).queue();
            transformer.setEmojiSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_emoji_spam", transformer.getEmojiSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runMentionUpdateCommand(CommandMessage context, String[] args,
            GlobalSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update mention spam to " + args[1]).queue();
            transformer.setMassMention(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_mass_mention", transformer.getMassMention());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean updateRecordInDatabase(CommandMessage context, String table, int setTo) {
        try {

            avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_SETTINGS_TABLE)
                    .where("main_group_id", context.getGlobalSettingsTransformer().getMainGroupId())
                    .update(statement -> statement.set(table, setTo));

            context.makeSuccess("Updated!").queue();

            long mgmLogs = context.getGlobalSettingsTransformer().getMgmLogsId();
            if (mgmLogs != 0) {
                TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                if (tc != null) {
                    tc.sendMessageEmbeds(
                            context.makeInfo("[``:tableSetting`` was changed to ``:value`` by :mention](:link)")
                                    .set("tableSetting", table).set("value", setTo)
                                    .set("mention", context.getMember().getAsMention())
                                    .set("link", context.getMessage().getJumpUrl()).buildEmbed())
                            .queue();
                }
            }

            return true;
        } catch (SQLException throwables) {
            Xeus.getLogger().error("ERROR: ", throwables);
            context.makeError("Database error!").queue();
            return false;
        }
    }

    private boolean handleRoleSetupArguments(CommandMessage context, String[] args) {
        Role role = MentionableUtil.getRole(context.getMessage(), new String[] { args[0] });
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

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleRole(CommandMessage context, Role role, String rank,
            ComparatorUtil.ComparatorType value) {
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
            // log.error("Failed to save the level exempt roles to the database for guild
            // {}, error: {}",
            // context.getGuild().getId(), e.getMessage(), e
            // );

            context.makeError(
                    "Failed to save the changes to the database, please try again. If the issue persists, please contact one of my developers.")
                    .queue();

            return false;
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

    private void runModRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set<Long> mods) {
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

    private void runGroupShoutRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set<Long> groupShouts) {
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

    private void runAdminRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set<Long> admins) {
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

    private void runNoLinksRolesCheck(CommandMessage context, boolean b, StringBuilder sb, Set<Long> admins) {
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
        if (transformer.getLeadRoles().isEmpty() && transformer.getHRRoles().isEmpty()
                && transformer.getLeadRoles().isEmpty() && transformer.getNoLinksRoles().isEmpty()
                && transformer.getMainDiscordRole() == 0 && transformer.getRobloxGroupId() == 0) {
            return command.sendErrorMessage(context,
                    "Sorry, but there are no manager, admin, mod, main role id, roblox group id or no-links roles on the discord configured.");
        }

        Set<Long> mod = transformer.getHRRoles();
        Set<Long> admins = transformer.getLeadRoles();
        Set<Long> noLinks = transformer.getNoLinksRoles();
        Set<Long> groupShouts = transformer.getGroupShoutRoles();
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

}
