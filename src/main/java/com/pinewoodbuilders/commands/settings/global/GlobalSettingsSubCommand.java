package com.pinewoodbuilders.commands.settings.global;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.contracts.commands.settings.SettingsSubCommand;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.CheckPermissionUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;

public class GlobalSettingsSubCommand extends SettingsSubCommand {

    public GlobalSettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        super(avaire, command);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();
        int permission = CheckPermissionUtil.getPermissionLevel(guildTransformer, context.guild, context.member).getLevel();
        if (permission < CheckPermissionUtil.GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel()) {
            return command.sendErrorMessage(context, "Sorry, but you do not have the permissions required to run this command.");
        }

        if (guildTransformer == null) {
            context.makeError("Server settings could not be gathered").queue();
            return false;
        }

        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledRoles(context, guildTransformer);
        }

        switch (args[0].toLowerCase()) {
            case "al":
            case "audit-logs":
                return runAuditLogsCommand(context, Arrays.copyOfRange(args, 1, args.length), guildTransformer);
            case "gfilter":
            case "global-filter":
            case "gf":
            case "f":
            case "filter":
            case "globalf":
                return runGlobalFilterCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "tnms":
            case "toggle-new-moderation-system":
            case "nms":
            case "new-moderation-system":
                return newModerationSystem(context, guildTransformer);
            default:
                context.makeInfo(
                        "God damn, please get this command right.\n\n- `tnms` -> Toggle the new moderation system\n - `al` -> Set the audit log in all connected guilds.\n - `filter` -> Set the filter across the servers\n")
                    .queue();
                return false;
        }

    }

    private boolean newModerationSystem(CommandMessage context, GuildSettingsTransformer guildTransformer) {
        GlobalSettingsTransformer settings = guildTransformer.getGlobalSettings();
        if (settings == null) return command.sendErrorMessage(context, "Global settings have not been set.");

        settings.setNewWarnSystem(!settings.getNewWarnSystem());
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_SETTINGS_TABLE).where("main_group_id", settings.getMainGroupId())
                .update(l -> {
                    l.set("new_moderation_system", true);
                });
            context.makeSuccess((settings.getNewWarnSystem() ? "Enabled" : "Disabled") + " the new moderation system in " + settings.getMainGroupName()).queue();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }



    private boolean runGlobalFilterCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeInfo("Please use `pwcf` or `pef` to select what global filter you'd like to edit.").queue();
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "wcf":
            case "pwcf":
                return runEditWCFFilter(context, Arrays.copyOfRange(args, 1, args.length));
            case "ef":
            case "pef":
                return runEditPEFFilter(context, Arrays.copyOfRange(args, 1, args.length));
            default:
                context.makeInfo("Please use `pwcf` or `pef` to select what global filter you'd like to edit.").queue();
                return false;

        }
    }

    private boolean runEditPEFFilter(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return command.sendErrorMessage(context, "You didn't give any arguments.");
        }

        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();
        if (guildTransformer == null) {
            return command.sendErrorMessage(context,
                "Unable to load the local server settings. (We need this to get the MGI Connected to this guild)");
        }

        if (guildTransformer.getMainGroupId() == 0 && !guildTransformer.isOfficialSubGroup()) {
            return command.sendErrorMessage(context,
                "A main group ID has not been set for this guild, or this is not an official server. So I don't know what settings to edit securely. Please set a MGI on this server, and make sure it's an officially connected server!");
        }

        GlobalSettingsTransformer transformer = context.getGuildSettingsTransformer().getGlobalSettings();
        if (transformer == null) {
            return command.sendErrorMessage(context,
                "You have not set the MGI in your guild settings, so I don't know what global settings to edit.");
        }

        String words = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
        if (words.contains(" ")) {
            return command.sendErrorMessage(context,
                "The EXACT words in the filter are not allowed to contain any spaces, use `!exactfilter`");
        }
        if (args[0].equalsIgnoreCase("list")) {
            return getAutoModExactList(context, transformer);
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length == 1) {
                return command.sendErrorMessage(context, "You didn't give any words to remove from the global filter.");
            }
            return removeAutoModExact(context, transformer, words);
        }
        if (args[0].equalsIgnoreCase("add")) {
            if (args.length == 1) {
                return command.sendErrorMessage(context, "You didn't give any words to add to the global filter.");
            }
            transformer.getGlobalFilterExact().add(words);
            try {
                updateGuildAutoModExact(context, transformer);

                context.makeSuccess("Successfully added: ``" + words + "``").queue();

                long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
                if (mgmLogs != 0) {
                    TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                    if (tc != null) {
                        tc.sendMessageEmbeds(context.makeInfo(
                                "[The following words have been added to the **GLOBAL** exact filter by :user](:link):\n"
                                    + "```:words```")
                            .set("words", words).set("user", context.getMember().getAsMention())
                            .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
                    }
                }

                return true;
            } catch (SQLException e) {
                Xeus.getLogger().error("ERROR: ", e);
                return false;
            }
        } else {
            return command.sendErrorMessage(context, "Invalid argument. See ``!help exactfilter`` for the arguments.");
        }
    }

    private boolean getAutoModExactList(CommandMessage context, GlobalSettingsTransformer transformer) {
        context.makeSuccess(
                "This the list of the current filtered EXACT words: \n```" + transformer.getGlobalFilterExact() + "```")
            .queue();
        return false;
    }

    private boolean removeAutoModExact(CommandMessage context, GlobalSettingsTransformer transformer, String args) {
        if (!transformer.getGlobalFilterExact().contains(args)) {
            return command.sendErrorMessage(context, "This word does not exist in the list");
        }

        transformer.getGlobalFilterExact().remove(args);

        try {
            updateGuildAutoModExact(context, transformer);

            context.makeSuccess("Deleted: " + args).queue();
            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
            return false;
        }
    }

    private void updateGuildAutoModExact(CommandMessage message, GlobalSettingsTransformer transformer)
        throws SQLException {
        for (String id : Constants.guilds) {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", id)
                .update(statement -> statement.set("global_filter_exact",
                    Xeus.gson.toJson(transformer.getGlobalFilterExact()), true));
        }
    }

    private boolean runEditWCFFilter(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return command.sendErrorMessage(context, "You didn't give any arguments.");
        }

        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();
        if (guildTransformer == null) {
            return command.sendErrorMessage(context,
                "Unable to load the local server settings. (We need this to get the MGI Connected to this guild)");
        }

        if (guildTransformer.getMainGroupId() == 0 && !guildTransformer.isOfficialSubGroup()) {
            return command.sendErrorMessage(context,
                "A main group ID has not been set for this guild, or this is not an official server. So I don't know what settings to edit securely. Please set a MGI on this server, and make sure it's an officially connected server!");
        }

        GlobalSettingsTransformer transformer = context.getGuildSettingsTransformer().getGlobalSettings();
        if (transformer == null) {
            return command.sendErrorMessage(context,
                "You have not set the MGI in your guild settings, so I don't know what global settings to edit.");
        }

        String words = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
        if (args[0].equalsIgnoreCase("list")) {
            return getGlobalAutoModWildcardList(context, transformer);
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length == 1) {
                return command.sendErrorMessage(context, "You didn't give any words to remove from the global filter.");
            }
            return removeAutoModWildcard(context, transformer, words);
        }
        if (args[0].equalsIgnoreCase("add")) {
            if (args.length == 1) {
                return command.sendErrorMessage(context, "You didn't give any words to add to the global filter.");
            }
            transformer.getGlobalFilterWildcard().add(words);
            try {
                updateGuildAutoModWildcard(context, transformer);

                context.makeSuccess("Successfully added: ``" + words + "``").queue();

                long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
                if (mgmLogs != 0) {
                    TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                    if (tc != null) {
                        tc.sendMessageEmbeds(context.makeInfo(
                                "[The following words have been added to the **GLOBAL** wildcard filter by :user](:link):\n"
                                    + "```:words```")
                            .set("words", words).set("user", context.getMember().getAsMention())
                            .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
                    }
                }
                return true;
            } catch (SQLException e) {
                Xeus.getLogger().error("ERROR: ", e);
                return false;
            }
        }
        if (args[0].equalsIgnoreCase("add-comma")) {
            if (args.length == 1) {
                return command.sendErrorMessage(context, "You didn't give any words to add to the global filter.");
            }
            transformer.getGlobalFilterWildcard().add(words);
            try {
                updateGuildAutoModWildcard(context, transformer);

                context.makeSuccess("Successfully added: ``" + words + "``").queue();
                return true;
            } catch (SQLException e) {
                Xeus.getLogger().error("ERROR: ", e);
                return false;
            }
        } else {
            return command.sendErrorMessage(context, "Invalid argument.");
        }
    }

    private boolean removeAutoModWildcard(CommandMessage context, GlobalSettingsTransformer transformer, String args) {
        if (!transformer.getGlobalFilterWildcard().contains(args)) {
            return command.sendErrorMessage(context, "This word does not exist in the wildcard list");
        }

        transformer.getGlobalFilterWildcard().remove(args);
        try {
            updateGuildAutoModWildcard(context, transformer);

            context.makeSuccess("Deleted: ``" + args + "``").queue();
            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
            return false;
        }
    }

    private boolean getGlobalAutoModWildcardList(CommandMessage context, GlobalSettingsTransformer transformer) {
        context.makeSuccess("This the list of the current filtered wildcard words: \n```"
            + transformer.getGlobalFilterWildcard() + "```").queue();
        return false;
    }

    private void updateGuildAutoModWildcard(CommandMessage message, GlobalSettingsTransformer transformer)
        throws SQLException {

        avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_SETTINGS_TABLE).where("main_group_id", transformer.getMainGroupId())
            .update(statement -> statement.set("global_filter_wildcard",
                Xeus.gson.toJson(transformer.getGlobalFilterWildcard()), true));


    }

    private boolean runAuditLogsCommand(CommandMessage context, String[] args,
                                        GuildSettingsTransformer transformer) {


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
                                                  GuildSettingsTransformer transformer) {
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
                                              GuildSettingsTransformer transformer) {
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
                                                GuildSettingsTransformer transformer) {
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
                                             GuildSettingsTransformer transformer) {
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
                                              GuildSettingsTransformer transformer) {
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
                                            GuildSettingsTransformer transformer) {
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
                .where("main_group_id", context.getGuildSettingsTransformer().getGlobalSettings().getMainGroupId())
                .update(statement -> statement.set(table, setTo));

            context.makeSuccess("Updated!").queue();

            long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
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
        if (transformer.getLeadRoles().isEmpty() && transformer.getHRRoles().isEmpty()
            && transformer.getLeadRoles().isEmpty() && transformer.getNoLinksRoles().isEmpty()
            && transformer.getMainDiscordRole() == 0 && transformer.getRobloxGroupId() == 0) {
            return command.sendErrorMessage(context,
                "Sorry, but there are no manager, admin, mod, main role id, roblox group id or no-links roles on the discord configured.");
        }

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
