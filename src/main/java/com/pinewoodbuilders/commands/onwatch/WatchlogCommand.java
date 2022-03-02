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

package com.pinewoodbuilders.commands.onwatch;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WatchlogCommand extends Command {

    public WatchlogCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "WatchLog Command";
    }

    @Override
    public String getDescription() {
        return "Displays the watch logging status for the server if no arguments is given, you can also mention a text channel to enable watchlogging and set it to the mentioned channel.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command` - Displays the current state of the watchlog module for the server.",
            "`:command <channel>` - Enabled watchlogging and sets it to the mentioned channel.",
            "`:command disable` - Disables the watchlogging module for the server."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command` - ",
            "`:command #watchlog` - Enables watchlogging and sets it to the watchlog channel.",
            "`:command disable` - Disables watchlogging for the server."
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Arrays.asList(
            WatchlogHistoryCommand.class,
            WatchlogReasonCommand.class
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("watchlog");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "isGuildLeadership",
            "throttle:user,1,5"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.ON_WATCH);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        if (args.length == 0) {
            sendModlogChannel(context, transformer).queue();
            return true;
        }

        if (ComparatorUtil.isFuzzyFalse(args[0])) {
            return disableModlog(context, transformer);
        }

        GuildChannel channel = MentionableUtil.getChannel(context.getMessage(), args);
        if (channel == null || !(channel instanceof TextChannel)) {
            return sendErrorMessage(context, context.i18n("mustMentionTextChannel"));
        }

        if (!((TextChannel) channel).canTalk() || !context.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
            return sendErrorMessage(context, context.i18n("cantSendEmbedMessages", ((TextChannel) channel).getAsMention()));
        }

        try {
            updateModlog(transformer, context, channel.getIdLong());

            context.makeSuccess(context.i18n("enable"))
                .set("on_watch", ((TextChannel) channel).getAsMention())
                .queue();
        } catch (SQLException ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }

        return true;
    }

    private boolean disableModlog(CommandMessage context, GuildSettingsTransformer transformer) {
        try {
            updateModlog(transformer, context, 0);

            context.makeSuccess(context.i18n("disable"))
                .queue();
        } catch (SQLException ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }

        return true;
    }

    private PlaceholderMessage sendModlogChannel(CommandMessage context, GuildSettingsTransformer transformer) {
        if (transformer.getOnWatchChannel() == 0) {
            return context.makeWarning(context.i18n("disabled"));
        }

        TextChannel modlogChannel = context.getGuild().getTextChannelById(transformer.getOnWatchChannel());
        if (modlogChannel == null) {
            try {
                updateModlog(transformer, context, 0);
            } catch (SQLException ex) {
                Xeus.getLogger().error(ex.getMessage(), ex);
            }
            return context.makeInfo(context.i18n("disabled"));
        }

        return context.makeSuccess(context.i18n("enabled"))
            .set("on_watch", modlogChannel.getAsMention());
    }

    private void updateModlog(GuildSettingsTransformer transformer, CommandMessage context, long value) throws SQLException {
        transformer.setOnWatchChannel(value);
        avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
            .where("id", context.getGuild().getId())
            .update(statement -> statement.set("on_watch_channel", value));
    }
}
