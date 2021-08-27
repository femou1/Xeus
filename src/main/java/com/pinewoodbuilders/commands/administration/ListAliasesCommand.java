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

package com.pinewoodbuilders.commands.administration;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.SimplePaginator;
import com.pinewoodbuilders.commands.CommandContainer;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.NumberUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListAliasesCommand extends Command {

    public ListAliasesCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Aliases Command";
    }

    @Override
    public String getDescription() {
        return "Lists all the existing command aliases.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Lists all the aliases for the server.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("aliases", "aliaslist");
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Collections.singletonList(AliasCommand.class);
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "throttle:channel,1,5",
            "require:user,general.manage_server"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.COMMAND_CUSTOMIZATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        if (transformer.getAliases().isEmpty()) {
            return sendErrorMessage(context, context.i18n("noAliases", generateCommandPrefix(context.getMessage())));
        }

        SimplePaginator<String> paginator = new SimplePaginator<>(transformer.getAliases(), 10);
        if (args.length > 0) {
            paginator.setCurrentPage(NumberUtil.parseInt(args[0], 1));
        }

        List<String> messages = new ArrayList<>();
        paginator.forEach((index, key, val) -> {
            String trigger = val.split(" ")[0];

            CommandContainer container = CommandHandler.getRawCommand(trigger);
            if (container != null) {
                val = val.replaceFirst(trigger, container.getCommand().generateCommandTrigger(context.getMessage()));
            }

            messages.add(String.format("`%s` => `%s`", key, val));
        });
        messages.add("\n" + paginator.generateFooter(context.getGuild(), generateCommandTrigger(context.getMessage())));

        context.makeSuccess(String.join("\n", messages))
            .setTitle(context.i18n("listAliases", paginator.getTotal()))
            .requestedBy(context.getMember())
            .queue();

        return true;
    }
}
