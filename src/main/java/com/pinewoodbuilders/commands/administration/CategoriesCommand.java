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

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.Category;
import com.pinewoodbuilders.commands.CategoryHandler;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.ChannelTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.ThreadChannel;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CategoriesCommand extends Command {

    public CategoriesCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Categories Command";
    }

    @Override
    public String getDescription() {
        return "Shows status of all command categories in the current or mentioned channel, both for globally and per-channel.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command [channel]` - Displays the status of the command categories in the mentioned channel, or the current channel if no channel was mentioned.");
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command #general`");
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Arrays.asList(ToggleCategoryCommand.class, ChangePrefixCommand.class);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("categories", "cats");
    }

    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.LOW;
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.COMMAND_CUSTOMIZATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildChannel channel = context.getGuildChannel();
        if (!context.getMentionedChannels().isEmpty()) {
            channel = context.getMentionedChannels().get(0);
        }

        GuildTransformer guildTransformer = context.getGuildTransformer();
        if (guildTransformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        ChannelTransformer transformer = (channel instanceof ThreadChannel threadChannel) ? guildTransformer.getChannel(threadChannel.getParentChannel().getId()) : guildTransformer.getChannel(channel.getId());

        List<String> items = new ArrayList<>();
        for (Category category : CategoryHandler.getValues()) {
            if (category.isGlobalOrSystem()) continue;

            if (category.getName().equalsIgnoreCase("System")) {
                items.add(Constants.EMOTE_DND + category.getName());
                continue;
            }

            if (transformer.isCategoryDisabled(category)) {
                items.add(Constants.EMOTE_AWAY + category.getName());
                continue;
            }

            items.add(Constants.EMOTE_ONLINE + category.getName());
        }

        context.makeInfo(context.i18n("status") + "\n\n" + String.join("\n", items))
            .setTitle(context.i18n("title", channel.getName()))
            .set("emoteEnabled", Constants.EMOTE_ONLINE)
            .set("emoteDisabledInChannel", Constants.EMOTE_AWAY)
            .set("emoteDisabledGlobally", Constants.EMOTE_DND)
            .queue();

        return true;
    }
}
