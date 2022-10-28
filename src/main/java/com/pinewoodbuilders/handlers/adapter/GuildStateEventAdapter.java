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

package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.ConsoleColor;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.metrics.Metrics;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.RestActionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateRegionEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;

import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;

public class GuildStateEventAdapter extends EventAdapter {

    /**
     * Instantiates the event adapter and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public GuildStateEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .useAsync(true)
                .where("id", event.getGuild().getId())
                .update(statement -> statement.set("name", event.getGuild().getName(), true));
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    public void onChannelUpdateRegion(ChannelUpdateRegionEvent event) {
        Metrics.geoTracker.labels(event.getOldValue().getName()).dec();
        Metrics.geoTracker.labels(event.getNewValue().getName()).inc();
    }

    public void onGuildJoin(GuildJoinEvent event) {
        Xeus.getLogger().info(ConsoleColor.format(
            "%greenJoined guild with an ID of " + event.getGuild().getId() + " called: " + event.getGuild().getName() + "%reset"
        ));

        if (!avaire.areWeReadyYet()) {
            return;
        }

        Metrics.guilds.inc();
        //Metrics.geoTracker.labels(event.getGuild().getRegion().getName()).inc();

        TextChannel channel = avaire.getShardManager().getTextChannelById(
            avaire.getConstants().getActivityLogChannelId()
        );

        if (channel == null) {
            return;
        }

        event.getGuild().retrieveOwner().queue(
            owner -> sendGuildJoinMessage(event, channel, owner),
            error -> sendGuildJoinMessage(event, channel, null)
        );
    }

    private void sendGuildJoinMessage(GuildJoinEvent event, TextChannel channel, Member owner) {
        double guildMembers = event.getGuild().getMembers().stream().filter(member -> !member.getUser().isBot()).count();
        double guildBots = event.getGuild().getMembers().stream().filter(member -> member.getUser().isBot()).count();
        double percentage = (guildBots / (guildBots + guildMembers)) * 100;

        channel.sendMessageEmbeds(
            new EmbedBuilder()
                .setColor(Color.decode("#66BB6A"))
                .setTimestamp(Instant.now())
                .setFooter(String.format("%s Users, and %s Bots, %s Bots",
                    NumberUtil.formatNicely(guildMembers),
                    NumberUtil.formatNicely(guildBots),
                    NumberUtil.formatNicelyWithDecimals(percentage) + "%"
                ), null)
                .addField("Added", String.format("%s (ID: %s)",
                    event.getGuild().getName(), event.getGuild().getId()
                ), false)
                .addField("Owner", owner == null ? "Unknown (Was not found!)" : String.format("%s (ID: %s)",
                    owner.getUser().getAsTag(), owner.getId()
                ), false)
                .build()
        ).queue(null, RestActionUtil.ignore);
    }

    public void onGuildLeave(GuildLeaveEvent event) {
        handleSendGuildLeaveWebhook(event.getGuild());
    }

    private void handleSendGuildLeaveWebhook(Guild guild) {
        Xeus.getLogger().info(ConsoleColor.format(
            "%redLeft guild with an ID of " + guild.getId() + " called: " + guild.getName() + "%reset"
        ));

        if (!avaire.areWeReadyYet()) {
            return;
        }

        Metrics.guilds.dec();
//        Metrics.geoTracker.labels(guild.getRegion().getName()).dec();

        TextChannel channel = avaire.getShardManager().getTextChannelById(
            avaire.getConstants().getActivityLogChannelId()
        );

        if (channel == null) {
            return;
        }

        channel.sendMessageEmbeds(
            new EmbedBuilder()
                .setColor(Color.decode("#EF5350"))
                .setTimestamp(Instant.now())
                .addField("Left/Removed", String.format("%s (ID: %s)",
                    guild.getName(), guild.getId()
                ), false)
                .build()
        ).queue(null, RestActionUtil.ignore);
    }
}
