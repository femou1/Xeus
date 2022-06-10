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
import com.pinewoodbuilders.utilities.ComparatorUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ThreadChannel;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ToggleCategoryCommand extends Command {

    public ToggleCategoryCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Toggle Category Command";
    }

    @Override
    public String getDescription() {
        return "This command allows you to toggle command categories on/off for the " +
            "current channel or the whole server in one go, this is useful if you " +
            "like some features in the bot but not others.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <category> <channel/global> [status]` - Changes the command " +
                "category status for the mentioned channel or globally if specified."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command fun global off` - Disables all the fun on the server D:",
            "`:command util #general off` - Disables all the utility commands in the general channel."
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Arrays.asList(CategoriesCommand.class, ChangePrefixCommand.class);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("togglecategory", "tcategory", "tcat");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "throttle:guild,1,4",
            "require:user,general.administrator"
        );
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
        if (args.length < 1) {
            return sendErrorMessage(context, "errors.missingArgument", "category");
        }

        Category category = CategoryHandler.fromLazyName(args[0]);
        if (category == null || category.isGlobalOrSystem()) {
            return sendErrorMessage(context, context.i18n("invalidCategory"), args[0]);
        }

        if (args.length < 2) {
            return sendErrorMessage(context, context.i18n("missingArgumentType"));
        }

        if (context.getMessage().getMentions().getChannels().size() != 1) {
            return sendErrorMessage(context, context.i18n("invalidChannelOrGlobalString"));
        }

        String channelId = context.getMessage().getMentions().getChannels().get(0).getId();
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }


        ChannelTransformer channel = (context.getMessage().getMentions().getChannels().get(0) instanceof ThreadChannel threadChannel) ? transformer.getChannel(threadChannel.getParentChannel().getId()) : transformer.getChannel(channelId);
        //ChannelTransformer channel = transformer.getChannel(channelId);

        boolean status = channel.isCategoryDisabled(category);
        if (args.length > 2) {
            ComparatorUtil.ComparatorType type = ComparatorUtil.getFuzzyType(args[2]);
            if (!type.equals(ComparatorUtil.ComparatorType.UNKNOWN)) {
                status = type.getValue();
            }
        }

        if (!transformer.getCategories().containsKey(channelId)) {
            transformer.getCategories().put(channelId, new HashMap<>());
        }

        transformer.getCategories().get(channelId).
            put(category.getName().toLowerCase(), status ? "true" : "false");

        try {
            updateGuildCategories(context.getMessage(), transformer);

            context.makeSuccess(getStatusMessage(context))
                .set("category", category.getName())
                .set("channel", "<#" + channel.getId() + ">")
                .set("status", context.i18n("status." + (status ? "enabled" : "disabled")))
                .queue();
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
            return false;
        }

        return true;
    }

    private void updateGuildCategories(Message context, GuildTransformer transformer) throws SQLException {
        avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
            .where("id", context.getGuild().getId())
            .update(statement -> {
                statement.set("modules", Xeus.gson.toJson(transformer.getCategories()));
            });
    }

    private String getStatusMessage(CommandMessage context) {
        return context.i18n("update.category");
    }
}
