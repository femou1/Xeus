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
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.MentionableUtil;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChannelInfoCommand extends Command {

    public ChannelInfoCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Channel Info Command";
    }

    @Override
    public String getDescription() {
        return "Shows information about the channel the command was run in, or the mentioned channel.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command [channel]` - Gets information about the mentioned channel, or if no channel was mention, get information about the current channel.");
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command #general`",
            "`:command`"
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Collections.singletonList(ChannelIdCommand.class);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("channelinfo", "cinfo");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.INFORMATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildChannel channel = context.getGuildChannel();
        if (args.length > 0) {
            channel = MentionableUtil.getChannel(context.getMessage(), args);

            if (channel == null) {
                return sendErrorMessage(context, "noChannelsWithNameOrId", args[0]);
            }
        }

        Carbon time = Carbon.createFromOffsetDateTime(channel.getTimeCreated());

        PlaceholderMessage placeholder = MessageFactory.makeEmbeddedMessage(context.getChannel())
            .setColor(MessageType.INFO.getColor())
            .setTitle("#" + channel.getName())
            .addField(context.i18n("fields.id"), channel.getId(), true)
            .addField(context.i18n("fields.category"), getCategoryFor(channel), true);

        if (channel instanceof TextChannel) {
            TextChannel textChannel = (TextChannel) channel;

            String topic = context.i18n("noTopic");
            if (textChannel.getTopic() != null && textChannel.getTopic().trim().length() > 0) {
                topic = textChannel.getTopic();
            }

            placeholder
                .setDescription(topic)
                .addField(context.i18n("fields.position"), "" + textChannel.getPosition(), true)
                .addField(context.i18n("fields.users"), "" + textChannel.getMembers().size(), true)
                .addField(context.i18n("fields.nsfw"), textChannel.isNSFW() ? "ON" : "OFF", true);
        }

        if (channel instanceof VoiceChannel) {
            VoiceChannel voiceChannel = (VoiceChannel) channel;
            int bitRate = voiceChannel.getBitrate() / 1000;

            placeholder
                .addField(context.i18n("fields.bitRate"), bitRate + " kbps", true);
        }

        if (channel instanceof ThreadChannel) {
            ThreadChannel threadChannel = (ThreadChannel) channel;

            placeholder
                .addField("Channel Owner", threadChannel.getOwner().getEffectiveName(), true);
        }

        placeholder
            .addField(
                context.i18n("fields.createdAt"),
                time.format(context.i18n("timeFormat"))
                    + "\n*About " + shortenDiffForHumans(time) + "*",
                true
            )
            .requestedBy(context.getMember())
            .queue();

        return true;
    }

    private String getCategoryFor(GuildChannel channel) {
        if (channel instanceof VoiceChannel) {
            VoiceChannel voiceChannel = (VoiceChannel) channel;

            if (voiceChannel.getParentCategory() == null) {
                return "*No Category*";
            }
            return voiceChannel.getParentCategory().getName();
        }

        if (channel instanceof ThreadChannel) {
            ThreadChannel threadChannel = (ThreadChannel) channel;
            return threadChannel.getParentChannel().getName();
        }

        if (channel instanceof TextChannel) {
            TextChannel textChannel = (TextChannel) channel;
            if (textChannel.getParentCategory() == null) {
                return "*No Category*";
            }

            return textChannel.getParentCategory().getName();
        }


        return "*No Category*";
    }

    private String shortenDiffForHumans(Carbon carbon) {
        String diff = carbon.diffForHumans();
        if (!diff.contains("and")) {
            return diff;
        }
        return diff.split("and")[0] + "ago";
    }
}
