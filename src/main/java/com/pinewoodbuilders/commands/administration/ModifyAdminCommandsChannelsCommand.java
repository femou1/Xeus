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
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModifyAdminCommandsChannelsCommand extends Command {

    private static final Logger log = LoggerFactory.getLogger(ModifyAdminCommandsChannelsCommand.class);

    public ModifyAdminCommandsChannelsCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Modify Admin Command Channels Command";
    }
    @Override
    public String getDescription() {
        return "Toggles a channel (or all channels) as a admins cmd channel.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <channel> [status]` - Toggles the lock feature on/off.",
            "`:command` - Lists channels with their lock-ability enabled."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command #spam off` - Disables lock in the #spam channel.",
            "`:command #sandbox` - Toggles the lock on/off for the #sandbox channel.",
            "`:command` - Lists all the channels that currently have their lock-ability enabled."
        );
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("macc", "admin-command-channel", "acc", "modify-admin-command-channel");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isGuildLeadership",
            "throttle:user,1,5"
        );
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer guildTransformer = context.getGuildTransformer();
        if (guildTransformer == null) {
            context.makeError("Server settings could not be gathered").queue();
            return false;
        }

        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledChannels(context, guildTransformer);
        }

        GuildChannel channel = MentionableUtil.getChannel(context.getMessage(), args);
        if (!(channel instanceof TextChannel textChannel)) {
            return sendErrorMessage(context, "You must provide a valid text channel.");
        }

        if (!textChannel.canTalk()) {
            return sendErrorMessage(context, context.i18n("I can't talk in the {0} channel",
                textChannel.getAsMention()
            ));
        }

        if (args.length > 1) {
            return handleToggleChannel(context, textChannel, ComparatorUtil.getFuzzyType(args[1]));
        }
        return handleToggleChannel(context, textChannel, ComparatorUtil.ComparatorType.UNKNOWN);
    }

    private boolean sendEnabledChannels(CommandMessage context, GuildTransformer guildTransformer) {
        if (guildTransformer.getAdminCommandChannels().isEmpty()) {
            return sendErrorMessage(context, "There are currently no channels with the admin channel check enabled, you can add one by using the `"+generateCommandTrigger(context.getMessage())+" <channel>` ");
        }

        List <String> channels = new ArrayList <>();
        for (Long channelId : guildTransformer.getAdminCommandChannels()) {
            TextChannel textChannel = context.getGuild().getTextChannelById(channelId);
            if (textChannel != null) {
                channels.add(textChannel.getAsMention());
            }
        }

        context.makeInfo("All the channels mentioned below currently allow some specific admin to be ran in there, any channel not on this list is unaffected.\n\n:channels")
            .set("channels", String.join(", ", channels))
            .setTitle("Channels that only allow specific commands ("+guildTransformer.getAdminCommandChannels().size()+")")
            .queue();

        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleChannel(CommandMessage context, TextChannel channel, ComparatorUtil.ComparatorType value) {
        GuildTransformer guildTransformer = context.getGuildTransformer();

        switch (value) {
            case FALSE:
                guildTransformer.getAdminCommandChannels().remove(channel.getIdLong());
                break;

            case TRUE:
                guildTransformer.getAdminCommandChannels().add(channel.getIdLong());
                break;

            case UNKNOWN:
                if (guildTransformer.getAdminCommandChannels().contains(channel.getIdLong()))
                    guildTransformer.getAdminCommandChannels().remove(channel.getIdLong());
                else
                    guildTransformer.getAdminCommandChannels().add(channel.getIdLong());
                break;
        }

        boolean isEnabled = guildTransformer.getAdminCommandChannels().contains(channel.getIdLong());

        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("command_admin_channels", Xeus.gson.toJson(
                    guildTransformer.getAdminCommandChannels()
                ), true));

            context.makeSuccess("All admin commands have been **:status** for :channel!")
                .set("channel", channel.getAsMention())
                .set("status", isEnabled ? "Enabled" : "Disabled")
                .queue();

            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("Failed to save the level exempt channels to the data for guild {}, error: {}",
                context.getGuild().getId(), e.getMessage(), e
            );

            context.makeError("Failed to save the changes to the database, please try again. If the issue persists, please contact one of my developers.").queue();

            return false;
        }
    }
}
