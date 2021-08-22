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

package com.pinewoodbuilders.commands.system.plugin;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.SimplePaginator;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.system.PluginCommand;
import com.pinewoodbuilders.contracts.commands.plugin.PluginSubCommand;
import com.pinewoodbuilders.plugin.PluginHolder;
import com.pinewoodbuilders.plugin.PluginLoader;
import com.pinewoodbuilders.utilities.NumberUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListPlugins extends PluginSubCommand {

    /**
     * Creates a new plugin sub command instance.
     *
     * @param avaire  The main avaire application instance.
     * @param command The parent plugin command instance.
     */
    public ListPlugins(Xeus avaire, PluginCommand command) {
        super(avaire, command);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return command.sendErrorMessage(context, String.join("\n", Arrays.asList(
                "You must choose the type of listing you want to see!",
                "",
                "Installed plugins: `installed` `i`",
                "Available plugins: `available`, `a`"
            )));
        }

        if (args[0].equalsIgnoreCase("installed") || args[0].equalsIgnoreCase("i")) {
            if (avaire.getPluginManager().getPlugins().isEmpty()) {
                return command.sendErrorMessage(context, "There are currently no installed plugins!");
            }

            List<String> messages = new ArrayList<>();
            SimplePaginator<PluginLoader> paginator = new SimplePaginator<>(
                avaire.getPluginManager().getPlugins().iterator(), 5, 1
            );

            if (args.length > 1) {
                paginator.setCurrentPage(NumberUtil.parseInt(args[1], 1));
            }

            paginator.forEach((index, key, loader) -> {
                messages.add("**" + loader.getName() + "**");
            });

            messages.add("\n" + paginator.generateFooter(context.getGuild(), command.generateCommandTrigger(context.getMessage()) + " list installed"));

            context.makeInfo(String.join("\n", messages))
                .setTitle("Installed Plugins")
                .queue();

            return true;
        }

        if (args[0].equalsIgnoreCase("available") || args[0].equalsIgnoreCase("a")) {
            List<PluginHolder> officialPluginsList = avaire.getPluginManager().getOfficialPluginsList();
            if (officialPluginsList == null) {
                return command.sendErrorMessage(context, "Failed to load plugins for the plugins list, try again later.");
            }

            List<String> messages = new ArrayList<>();
            SimplePaginator<PluginHolder> paginator = new SimplePaginator<>(officialPluginsList, 5, 1);
            if (args.length > 1) {
                paginator.setCurrentPage(NumberUtil.parseInt(args[1], 1));
            }

            paginator.forEach((index, key, loader) -> {
                messages.add("**" + loader.getName() + "**");
            });

            messages.add("\n" + paginator.generateFooter(context.getGuild(), command.generateCommandTrigger(context.getMessage()) + " list available"));

            context.makeInfo(String.join("\n", messages))
                .setTitle("Available Plugins")
                .queue();

            return false;
        }

        return command.sendErrorMessage(context, "Invalid listing type given!");
    }
}
