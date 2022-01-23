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
import com.pinewoodbuilders.Constants;
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

public class ModifyLockChannelCommand extends Command {

    private static final Logger log = LoggerFactory.getLogger(ModifyLockChannelCommand.class);

    public ModifyLockChannelCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Modify Lockable Channels Command";
    }

    @Override
    public String getDescription() {
        return "Toggles a channel (or all channels) as a lockable channel.";
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
    public List <Class <? extends Command>> getRelations() {
        return Arrays.asList(
            ModifyRoleChannelLockCommand.class,
            LockChannelsCommand.class
        );
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("channel-locks", "cl");
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
        return Collections.singletonList(CommandGroups.CHANNEL_LOCK);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer guildTransformer = context.getGuildTransformer();
        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledChannels(context, guildTransformer);
        }

        GuildChannel channel = MentionableUtil.getChannel(context.getMessage(), args);
        if (!(channel instanceof TextChannel textChannel)) {
            return sendErrorMessage(context, context.i18n("invalidChannel"));
        }

        if (!textChannel.canTalk()) {
            return sendErrorMessage(context, context.i18n("cantTalkInChannel",
                textChannel.getAsMention()
            ));
        }

        if (args.length > 1) {
            return handleToggleChannel(context, textChannel, ComparatorUtil.getFuzzyType(args[1]));
        }
        return handleToggleChannel(context, textChannel, ComparatorUtil.ComparatorType.UNKNOWN);
    }

    private boolean sendEnabledChannels(CommandMessage context, GuildTransformer guildTransformer) {
        if (guildTransformer.getLockableChannels().isEmpty()) {
            return sendErrorMessage(context, context.i18n("noChannelsWithRewardsDisabled"),
                generateCommandTrigger(context.getMessage())
            );
        }

        List <String> channels = new ArrayList <>();
        for (Long channelId : guildTransformer.getLockableChannels()) {
            TextChannel textChannel = context.getGuild().getTextChannelById(channelId);
            if (textChannel != null) {
                channels.add(textChannel.getAsMention());
            }
        }

        context.makeInfo(context.i18n("listChannels"))
            .set("channels", String.join(", ", channels))
            .setTitle(context.i18n("listChannelsTitle",
                guildTransformer.getLockableChannels().size()
            ))
            .queue();

        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleChannel(CommandMessage context, TextChannel channel, ComparatorUtil.ComparatorType value) {
        GuildTransformer guildTransformer = context.getGuildTransformer();

        switch (value) {
            case FALSE:
                guildTransformer.getLockableChannels().remove(channel.getIdLong());
                break;

            case TRUE:
                guildTransformer.getLockableChannels().add(channel.getIdLong());
                break;

            case UNKNOWN:
                if (guildTransformer.getLockableChannels().contains(channel.getIdLong()))
                    guildTransformer.getLockableChannels().remove(channel.getIdLong());
                 else
                    guildTransformer.getLockableChannels().add(channel.getIdLong());
                break;
        }

        boolean isEnabled = guildTransformer.getLockableChannels().contains(channel.getIdLong());

        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("lockable_channels", Xeus.gson.toJson(
                    guildTransformer.getLockableChannels()
                ), true));

            context.makeSuccess(context.i18n("success"))
                .set("channel", channel.getAsMention())
                .set("status", context.i18n(isEnabled ? "status.enabled" : "status.disabled"))
                .queue();

            return true;
        } catch (SQLException e) {
            log.error("Failed to save the level exempt channels to the data for guild {}, error: {}",
                context.getGuild().getId(), e.getMessage(), e
            );

            context.makeError(context.i18n("failedToUpdate")).queue();

            return false;
        }
    }
}
