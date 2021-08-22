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

package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandContainer;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.help.HelpCommand;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SourceCommand extends Command {

    private final String rootUrl = "https://gitlab.com/pinewood-builders/discord/xeus";

    public SourceCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Source Command";
    }

    @Override
    public String getDescription() {
        return "Gives you the source code for the Bot, or the code for a given command.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command` - Returns the full source code for the bot.",
            "`:command <command>` - Returns the source code for the given command."
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Collections.singletonList(HelpCommand.class);
    }


    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command ping`");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("source");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.BOT_INFORMATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeInfo(context.i18n("noArgs") + "\n\n" + rootUrl).queue();
            return true;
        }

        CommandContainer command = getCommand(context.getMessage(), args[0]);
        if (command == null) {
            context.makeInfo(context.i18n("invalidCommand") + "\n\n" + rootUrl).queue();
            return true;
        }

        String sourceUri = command.getSourceUri();
        if (sourceUri == null) {
            context.makeInfo(context.i18n("externalCommand") + "\n\n" + rootUrl).queue();
            return true;
        }

        context.makeInfo(context.i18n("command") + "\n\n" + sourceUri)
            .set("command", command.getCommand().getName())
            .queue();

        return true;
    }

    private CommandContainer getCommand(Message message, String commandString) {
        CommandContainer command = CommandHandler.getCommand(message, commandString);
        if (command != null) {
            return command;
        }
        return CommandHandler.getLazyCommand(commandString);
    }
}
