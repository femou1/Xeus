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
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.ChannelTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.permissions.Permissions;
import com.pinewoodbuilders.utilities.StringReplacementUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.SQLException;

public class MemberEventAdapter extends EventAdapter {

    private static final Logger log = LoggerFactory.getLogger(MemberEventAdapter.class);

    /**
     * Instantiates the event adapter and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public MemberEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildTransformer transformer = GuildController.fetchGuild(avaire, event.getGuild());
        if (transformer == null) {
            log.warn("Failed to get a valid guild transformer during member join! User:{}, Guild:{}",
                event.getMember().getUser().getId(), event.getGuild().getId()
            );
            return;
        }

        for (ChannelTransformer channelTransformer : transformer.getChannels()) {
            if (channelTransformer.getWelcome().isEnabled()) {
                TextChannel textChannel = event.getGuild().getTextChannelById(channelTransformer.getId());
                if (textChannel == null) {
                    continue;
                }

                if (!event.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_SEND)) {
                    continue;
                }

                String message = StringReplacementUtil.parse(
                    event.getGuild(), textChannel, event.getUser(),
                    channelTransformer.getWelcome().getMessage() == null ?
                        "Welcome %user% to **%server%!**" :
                        channelTransformer.getWelcome().getMessage()
                );

                String embedColor = channelTransformer.getWelcome().getEmbedColor();
                if (embedColor == null) {
                    textChannel.sendMessage(message).queue();
                    continue;
                }

                textChannel.sendMessageEmbeds(
                    MessageFactory.createEmbeddedBuilder()
                        .setDescription(message)
                        .setColor(Color.decode(embedColor))
                        .build()
                ).queue();
            }
        }

        if (event.getUser().isBot()) {
            return;
        }

        // Re-mutes the user if a valid mute role have been setup for the guild
        // and the user is still registered as muted for the server.
        if (transformer.getMuteRole() != null) {
            Role mutedRole = event.getGuild().getRoleById(transformer.getMuteRole());
            if (canGiveRole(event, mutedRole) && avaire.getMuteManger().isMuted(event.getGuild().getIdLong(), event.getUser().getIdLong())) {
                event.getGuild().addRoleToMember(
                    event.getMember(), mutedRole
                ).queue();
            }
        }

        if (transformer.getAutorole() != null) {
            Role role = event.getGuild().getRoleById(transformer.getAutorole());
            if (canGiveRole(event, role)) {
                event.getGuild().addRoleToMember(
                    event.getMember(), role
                ).queue();
            }
        }

        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.ROLE_PERSISTENCE_TABLE_NAME)
                .where("guild_id", event.getGuild().getId()).andWhere("user_id", event.getMember().getIdLong()).get();
            if (c.size() > 0) {
                c.forEach(p -> {
                    Role r = event.getGuild().getRoleById(p.getLong("role_id"));
                    Member m = event.getMember();

                    if (r != null) {
                        event.getGuild().addRoleToMember(m, r).queue();
                    }
                });
            }
        } catch (SQLException throwables) {
            return;
        }


        GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild());
        // Re-WATCHES the user if a valid WATCH role have been setup for the guild
        // and the user is still registered as WATCHED for the server.
        if (settings.getOnWatchRole() != 0) {
            Role watch = event.getGuild().getRoleById(settings.getOnWatchRole());
            if (canGiveRole(event, watch) &&
                (avaire.getOnWatchManger().isOnWatchd(event.getGuild().getIdLong(), event.getUser().getIdLong()) ||
                    avaire.getGlobalWatchManager()
                        .isGlobalWatched(settings.getMainGroupId(),
                        event.getMember().getIdLong(),
                        event.getGuild().getIdLong()))) {
                event.getGuild().addRoleToMember(
                    event.getMember(), watch
                ).queue();
            }
        }

        // Re-mutes the user if a valid mute role have been setup for the guild
        // and the user is still registered as muted for the server.
        if (transformer.getMuteRole() != null) {
            Role mutedRole = event.getGuild().getRoleById(transformer.getMuteRole());
            if (canGiveRole(event, mutedRole) && avaire.getGlobalMuteManager().isGlobalMuted(settings.getMainGroupId(), event.getMember().getIdLong(), event.getGuild().getIdLong())) {
                event.getGuild().addRoleToMember(
                    event.getMember(), mutedRole
                ).queue();
            }
        }

    }

    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        GuildTransformer transformer = GuildController.fetchGuild(avaire, event.getGuild());
        if (transformer == null) {
            log.warn("Failed to get a valid guild transformer during member leave! User:{}, Guild:{}",
                event.getMember().getUser().getId(), event.getGuild().getId()
            );
            return;
        }

        for (ChannelTransformer channelTransformer : transformer.getChannels()) {
            if (channelTransformer.getGoodbye().isEnabled()) {
                TextChannel textChannel = event.getGuild().getTextChannelById(channelTransformer.getId());
                if (textChannel == null) {
                    continue;
                }

                if (!event.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_SEND)) {
                    continue;
                }

                String message = StringReplacementUtil.parse(
                    event.getGuild(), textChannel, event.getUser(),
                    channelTransformer.getGoodbye().getMessage() == null ?
                        "%user% has left **%server%**! :(" :
                        channelTransformer.getGoodbye().getMessage()
                );

                String embedColor = channelTransformer.getGoodbye().getEmbedColor();
                if (embedColor == null) {
                    textChannel.sendMessage(message).queue();
                    continue;
                }

                textChannel.sendMessageEmbeds(
                    MessageFactory.createEmbeddedBuilder()
                        .setDescription(message)
                        .setColor(Color.decode(embedColor))
                        .build()
                ).queue();
            }
        }
    }

    private boolean canGiveRole(GuildMemberJoinEvent event, Role role) {
        return role != null
            && event.getGuild().getSelfMember().canInteract(role)
            && (event.getGuild().getSelfMember().hasPermission(Permissions.MANAGE_ROLES.getPermission())
            || event.getGuild().getSelfMember().hasPermission(Permissions.ADMINISTRATOR.getPermission()));
    }
}
