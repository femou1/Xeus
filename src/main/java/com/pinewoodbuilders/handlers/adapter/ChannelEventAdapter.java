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
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.ReactionController;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class ChannelEventAdapter extends EventAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChannelEventAdapter.class);

    /**
     * Instantiates the event adapter and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public ChannelEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onTextChannelDelete(ChannelDeleteEvent event, TextChannel channel) {
        if (event.getChannelType() == ChannelType.TEXT) {
            handleTextChannelDeleteReactionsRoles(event, channel);
            handleTextChannelDeleteGuildSettings(event, channel);
        }
    }

    private void handleTextChannelDeleteGuildSettings(ChannelDeleteEvent event, TextChannel channel) {
        GuildTransformer transformer = GuildController.fetchGuild(avaire, channel.getGuild());
        if (transformer == null) {
            return;
        }

        if (transformer.getModlog() != null && transformer.getModlog().equalsIgnoreCase(event.getChannel().getId())) {
            setDatabaseColumnToNull(channel.getGuild().getId(), "modlog");
        }

        if (transformer.getLevelChannel() != null && transformer.getLevelChannel().equals(event.getChannel().getId())) {
            setDatabaseColumnToNull(channel.getGuild().getId(), "level_channel");
        }

    }

    private void handleTextChannelDeleteReactionsRoles(ChannelDeleteEvent event, TextChannel channel) {
        Collection collection = ReactionController.fetchReactions(avaire, channel.getGuild());
        if (collection == null || collection.isEmpty()) {
            return;
        }

        if (collection.where("channel_id", event.getChannel().getId()).isEmpty()) {
            return;
        }

        try {
            avaire.getDatabase().newQueryBuilder(Constants.REACTION_ROLES_TABLE_NAME)
                .where("channel_id", event.getChannel().getId())
                .delete();

            ReactionController.forgetCache(channel.getGuild().getIdLong());
        } catch (SQLException e) {
            log.error("Failed to delete reaction roles from {} for channel ID {}, error: {}",
                channel.getGuild().getId(), event.getChannel().getId(), e.getMessage(), e
            );
        }
    }


    public void updateChannelData(Guild guild, TextChannel channel) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .useAsync(true)
                .where("id", guild.getId())
                .update(statement -> {
                    statement.set("channels_data", GuildController.buildChannelData(guild.getTextChannels()), true);
                });
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    private void setDatabaseColumnToNull(String guildId, String column) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .useAsync(true)
                .where("id", guildId)
                .update(statement -> statement.set(column, null));
        } catch (SQLException ignored) {
            //
        }
    }
}
