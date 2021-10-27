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
import com.pinewoodbuilders.blacklist.bot.Scope;
import com.pinewoodbuilders.chat.SimplePaginator;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BlacklistCommand extends Command {

    public BlacklistCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Blacklist Command";
    }

    @Override
    public String getDescription() {
        return "Add, Remove, and list users and servers on the blacklist.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList("`:command list` - Lists users and servers on the blacklist",
                "`:command remove <id>` - Removes the entry with the given ID from the blacklist",
                "`:command add <type> <id> <reason>` - Add the type with the given ID to the blacklist");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList("isPinewoodGuild", "isValidMGMMember");
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList("`:command add G 123` - Blacklists the guild with an ID of 123",
                "`:command add U 321 Doing stuff` - Blacklists the user with an ID of 321 for \"Doing stuff\"");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("blacklist");
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

        avaire.getBlacklist().getBlacklistEntities().forEach(entity -> {
            records.add(I18n.format("{0} **{1}** `{2}`\n ► _\"{3}\"_",
                    entity.getScope().getId() == 0 ? "\uD83E\uDD26" : "\uD83C\uDFEC", entity.getScope().getName(),
                    entity.getId(), entity.getReason() == null ? "No reason was given" : entity.getReason()));
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

        long id;
        try {
            id = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            return sendErrorMessage(context, "Invalid ID given, the ID must be a valid number value!");
        }

        if (!avaire.getBlacklist().isBlacklisted(id)) {
            return sendErrorMessage(context, "There are no records in the blacklist with an ID of `{0}`", "" + id);
        }

        avaire.getBlacklist().remove(id);

        context.makeSuccess("The Blacklist record with an ID of **:id** has been removed from the blacklist")
                .set("id", id).queue();

        return true;
    }

    private boolean addEntryToBlacklist(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Missing arguments, `type` and `id` argument required!");
        }

        Scope scope = Scope.parse(args[0]);
        if (scope == null) {
            return sendErrorMessage(context,
                    "Invalid type given, the type must be a valid blacklist scope!\nValid types are `G` for guilds/servers, or `U` for users.");
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

        avaire.getBlacklist().addIdToBlacklist(scope, id, reason);

        context.makeSuccess("The **:type** with an ID of **:id** has been added to the blacklist!")
                .set("type", scope.name().toLowerCase()).set("id", id).queue();

        long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
        if (mgmLogs != 0) {
            TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
            if (tc != null) {
                tc.sendMessageEmbeds(context.makeInfo(
                        "[<@:id> **(``:id``)** was blacklisted from Xeus everywhere in ``:guild`` by :punisher](:link)")
                        .set("id", id).set("guild", context.getGuild().getName())
                        .set("punisher", context.getMember().getAsMention())
                        .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
            }
        }
        return true;
    }
}
