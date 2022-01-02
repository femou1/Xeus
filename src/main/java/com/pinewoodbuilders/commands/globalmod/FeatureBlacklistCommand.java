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

package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.blacklist.features.FeatureScope;
import com.pinewoodbuilders.chat.SimplePaginator;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FeatureBlacklistCommand extends Command {

    public FeatureBlacklistCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Feature Blacklist Command";
    }

    @Override
    public String getDescription() {
        return "Add, Remove, and list users and servers on the feature blacklist.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList("`:command list` - Lists users and servers on the feature blacklist",
                "`:command remove <id>` - Removes the entry with the given ID from the feature blacklist",
                "`:command add <type> <id> <reason>` - Add the type with the given ID to the feature blacklist");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList("isPinewoodGuild", "isAdminOrHigher");
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
                "`:command add M 321920293939203910 Doing stuff` - Blacklists the user with an ID of 321 for \"Doing stuff\" from reports in the guild you ran the command in.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("feature-blacklist", "fb");
    }

    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.NORMAL;
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "Missing parameter, a valid `action` must be given!");
        }

        switch (args[0].toLowerCase()) {
            case "list":
                return listBlacklist(context, Arrays.copyOfRange(args, 1, args.length));

            case "add":
                return addEntryToBlacklist(context, Arrays.copyOfRange(args, 1, args.length));

            case "remove":
                return removeEntryFromBlacklist(context, Arrays.copyOfRange(args, 1, args.length));

            default:
                return sendErrorMessage(context, "Invalid `action` given, a valid `action` must be given!");
        }
    }

    private boolean listBlacklist(CommandMessage context, String[] args) {
        List<String> records = new ArrayList<>();

        avaire.getFeatureBlacklist().getBlacklistEntities().forEach(entity -> {
            records.add(I18n.format("{0} **{1}** `{2}` - `{3}`\n ► _\"{4}\"_",
                    entity.getScope().getId() == 0 ? "\uD83E\uDD26" : "\uD83C\uDFEC", entity.getScope().getName(),
                    entity.getId(), entity.getGuildId(),
                    entity.getReason() == null ? "NRF" : entity.getReason()));
        });

        SimplePaginator<String> paginator = new SimplePaginator<>(records, 10, 1);
        if (args.length > 0) {
            paginator.setCurrentPage(NumberUtil.parseInt(args[0], 1));
        }

        List<String> messages = new ArrayList<>();
        paginator.forEach((index, key, val) -> messages.add(val));

        context.makeInfo(String.join("\n", messages) + "\n\n"
                + paginator.generateFooter(context.getGuild(), generateCommandTrigger(context.getMessage()) + " list"))
                .setTitle("Blacklist Page #" + paginator.getCurrentPage()).queue();

        return false;
    }

    private boolean removeEntryFromBlacklist(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "Missing arguments, the `id` argument is required!");
        }

        FeatureScope s = FeatureScope.parse(args[0]);
        if (s == null) {
            StringBuilder sb = new StringBuilder();
            for (FeatureScope fs : FeatureScope.values()) {
                sb.append("\n - ``").append(fs.getPrefix()).append("`` (").append(fs.getName()).append(")");
            }
            return sendErrorMessage(context, "Invalid `scope` given. Possible scopes are:" + sb);
        }

        if (args.length == 1) {
            return sendErrorMessage(context, "Missing parameter, a valid `scope` must be given!");
        }

        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            return sendErrorMessage(context, "Invalid ID given, the ID must be a valid number value!");
        }

        User user;
        try {
            user = avaire.getShardManager().getUserById(id);
            if (user == null) {
                return sendErrorMessage(context, "User does not exists, please try again.");
            }
        } catch (NumberFormatException e) {
            return sendErrorMessage(context, "Invalid ID given, the ID must be a valid number value!");
        }

        long guildId;
        if (s.getId() != 0) {
            guildId = context.guild.getIdLong();
        } else {
            if (!(XeusPermissionUtil.getPermissionLevel(context)
                    .getLevel() > GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel())) {
                return sendErrorMessage(context,
                        "You're required to be level "
                                + GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel() + " (``"
                                + GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getRankName()
                                + "``) or higher to *globally* blacklist someone on all guilds!");
            }
            guildId = 1L;
        }

        if (!avaire.getFeatureBlacklist().isBlacklisted(user, guildId, s)) {
            return sendErrorMessage(context, "There are no records in the blacklist with an ID of `{0}`", "" + id);
        }

        avaire.getFeatureBlacklist().remove(id, guildId, s);

        context.makeSuccess(
                "The feature blacklist record with an ID of **:id** has been removed from the **:scope** blacklist in ``:guild``!")
                .set("id", id)
                .set("guild", s.getId() != 0 ? context.getGuild().getName() : "All **official** Pinewood Discords!")
                .set("scope", s.getName()).queue();

        return true;
    }

    private boolean addEntryToBlacklist(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Missing arguments, `type` and `id` argument required!");
        }

        FeatureScope featureScope = FeatureScope.parse(args[0]);
        if (featureScope == null) {
            StringBuilder sb = new StringBuilder();
            for (FeatureScope fs : FeatureScope.values()) {
                sb.append("\n - ``").append(fs.getPrefix()).append("`` (").append(fs.getName()).append(")");
            }
            return sendErrorMessage(context, "Invalid `scope` given. Possible scopes are:" + sb);
        }

        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            return sendErrorMessage(context, "Invalid ID given, the ID must be a valid number value!");
        }

        String reason = null;
        args = Arrays.copyOfRange(args, 2, args.length);
        if (args.length > 0) {
            reason = String.join(" ", args);
        }

        long guildId;
        if (featureScope.getId() == 0) {
            if (!(XeusPermissionUtil.getPermissionLevel(context)
                    .getLevel() > GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel())) {
                return sendErrorMessage(context,
                        "You're required to be level "
                                + GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel() + " (`"
                                + GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getRankName()
                                + "`) or higher to *globally* blacklist someone on all guilds!");
            }
            guildId = 1L;
        } else {
            guildId = context.guild.getIdLong();
        }

        avaire.getFeatureBlacklist().addIdToBlacklist(featureScope, id, reason, guildId);

        context.makeSuccess("The user with an ID of `:id` has been added to the **:type** blacklist of `:guild`!")
                .set("type", featureScope.getName()).set("id", id)
                .set("guild", featureScope.getId() != 0 ? context.getGuild().getName()
                        : "All **official** Pinewood Discords!")
                .queue();

        long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
        if (mgmLogs != 0) {
            TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
            if (tc != null) {
                tc.sendMessageEmbeds(context.makeInfo(
                        "[<@:id> **(`:id`)** was feature blacklisted for `:type` in `:guild` by :punisher](:link)")
                        .set("type", featureScope.getName()).set("id", id)
                        .set("punisher", context.getMember().getAsMention())
                        .set("guild", featureScope.getId() != 0 ? context.getGuild().getName() : "GLOBAL")
                        .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
            }
        }
        return true;
    }
}
