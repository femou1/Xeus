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

package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.MessageCache;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.contracts.cache.CachedMessage;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.moderation.global.punishments.globalban.GlobalBanContainer;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.TargetType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateAfkChannelEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBoostCountEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBoostTierEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSystemChannelEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR;

public class GuildEventAdapter extends EventAdapter {

    public GuildEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onGuildPIAMemberBanEvent(GuildUnbanEvent e) {
        GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, e.getGuild());

        boolean isBanned = avaire.getGlobalPunishmentManager().isGlobalBanned(transformer.getMainGroupId(), e.getUser().getId());
        if (isBanned) {
            List <GlobalBanContainer> unbanCollection = avaire.getGlobalPunishmentManager().getGlobalBans().get(transformer.getMainGroupId()).stream().filter(l -> {
                if (l.getUserId() != null) {
                    return l.getUserId().equals(e.getUser().getId());
                } else {
                    return false;
                }
            }).toList();

            e.getGuild().retrieveAuditLogs().queue(items -> {
                List <AuditLogEntry> logs = items.stream()
                    .filter(d -> d.getType().equals(ActionType.UNBAN)
                        && d.getTargetType().equals(TargetType.MEMBER)
                        && d.getTargetId().equals(e.getUser().getId())).toList();
                MessageChannel tc = avaire.getShardManager().getTextChannelById(Constants.PIA_LOG_CHANNEL);

                if (logs.size() < 1) {
                    if (tc != null) {
                        tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc).setDescription(e.getUser()
                                .getAsTag() + " has been unbanned from **" + e.getGuild()
                                + "**, however, I could not find the user responsible for the unban. Please check the audit logs in the responsible server for more information. (User has been re-banned)")
                            .buildEmbed()).queue();
                    }
                    e.getGuild().ban(UserSnowflake.fromId(e.getUser().getId()), 0,
                            "User was unbanned, user has been re-banned due to permban system in Xeus. Original ban reason (Do not unban without MGM permission): "
                                + unbanCollection.get(0).getReason())
                        .reason("PIA BAN: " + unbanCollection.get(0).getReason()).queue();
                } else {
                    if (tc != null) {
                        if (logs.get(0).getUser().equals(e.getJDA().getSelfUser()))
                            return;
                        if (XeusPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getGuild().getMember(logs.get(0).getUser())).getLevel() >= GuildPermissionCheckType.MAIN_GLOBAL_LEADERSHIP.getLevel())
                        if (XeusPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getGuild().getMemberById(logs.get(0).getUser().getId())).getLevel() >= MAIN_GLOBAL_MODERATOR.getLevel()) {
                            tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc)
                                .setDescription("**" + e.getUser().getName() + e.getUser().getDiscriminator()
                                    + "**" + " has been unbanned from **" + e.getGuild().getName()
                                    + "**\nIssued by MGM Moderator: " + logs.get(0).getUser().getName()
                                    + "#" + logs.get(0).getUser().getDiscriminator() + "\nWith reason: "
                                    + (logs.get(0).getReason() != null ? logs.get(0).getReason()
                                    : "No reason given"))
                                .buildEmbed()).queue();
                            logs.get(0).getUser().openPrivateChannel().queue(o -> {
                                if (o.getUser().isBot())
                                    return;
                                o.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc)
                                    .setDescription("You have currently unbanned a Global-Banned user.")
                                    .buildEmbed()).queue();
                            });
                        } else {
                            tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc)
                                .setDescription("**" + e.getUser().getName() + e.getUser().getDiscriminator()
                                    + "** has been unbanned from **" + e.getGuild().getName()
                                    + "**\nIssued by Guild Member: " + logs.get(0).getUser().getName() + "#"
                                    + logs.get(0).getUser().getDiscriminator()
                                    + " (User has been re-banned)")
                                .buildEmbed()).queue();

                            logs.get(0).getUser().openPrivateChannel().queue(o -> {
                                if (o.getUser().isBot())
                                    return;
                                o.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc).setDescription(
                                        "Sorry, but this user **:bannedUser** was permbanned of PB though the Xeus blacklist feature and may **not** be unbanned. Please ask a MGM Moderator to handle an unban if deemed necessary.")
                                    .set("bannedUser", e.getUser().getAsTag() + " / " + e.getUser().getName())
                                    .buildEmbed()).queue();
                            });

                            String agent = avaire.getShardManager().getUserById(unbanCollection.get(0).getPunisherId()) != null ?
                                avaire.getShardManager().getUserById(unbanCollection.get(0).getPunisherId()).getName()
                                : "No MGM Moderator found";
                            e.getGuild().ban(UserSnowflake.fromId(e.getUser().getId()), 0, "Banned by: " + agent + "\n" + "For: "
                                    + unbanCollection.get(0).getReason()
                                    + "\n*THIS IS A MGM GLOBAL BAN, DO NOT REVOKE THIS BAN WITHOUT CONSULTING THE MGM MODERATOR WHO INITIATED THE GLOBAL BAN, REVOKING THIS BAN WITHOUT MGM APPROVAL WILL RESULT IN DISCIPlINARY ACTION!*")
                                .reason("Global Ban, executed by " + agent + ". For: \n"
                                    + unbanCollection.get(0).getReason())
                                .queue();
                        }
                    }
                }
            });
        }

    }

    public void onGenericEvent(GenericEvent ev) {
        if (ev instanceof GenericMessageEvent event) {
            if (!event.isFromGuild()) return;
            GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild());
            if (transformer.getAuditLogsChannelId() != 0) {
                TextChannel tc = event.getGuild().getTextChannelById(transformer.getAuditLogsChannelId());
                if (tc != null) {
                    GuildTransformer guild = GuildController.fetchGuild(avaire, event.getGuild());
                    if (event instanceof MessageDeleteEvent e) {
                        if (guild.getIgnoredAuditLogChannels().contains(event.getChannel().getIdLong())) {return;}
                        if (e.isFromGuild()) {messageDeleteEvent(e, tc);}

                    } else if (event instanceof MessageUpdateEvent e) {
                        if (guild.getIgnoredAuditLogChannels().contains(event.getChannel().getIdLong())) {return;}
                        if (e.isFromGuild()) {messageUpdateEvent(e, tc);}
                    }
                }

            }
        } else if (ev instanceof GenericGuildEvent event) {
            GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild());
            if (transformer.getAuditLogsChannelId() != 0) {
                TextChannel tc = event.getGuild().getTextChannelById(transformer.getAuditLogsChannelId());
                if (tc != null) {
                    if (event instanceof GuildBanEvent e) {
                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 50, 0))
                            .setAuthor("User banned", null, e.getUser().getEffectiveAvatarUrl())
                            .setDescription(
                                e.getUser().getName() + "#" + e.getUser().getDiscriminator() + "**(:banned)**")
                            .set("banned", e.getUser().getAsMention()).setTimestamp(Instant.now()).queue();
                    } else if (event instanceof GuildUpdateAfkChannelEvent) {
                        GuildChannel oldChannel = getModifiedChannel(event, false);
                        GuildChannel newChannel = getModifiedChannel(event, true);

                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 0, 15))
                            .setAuthor("AFK Channel was modified", null, event.getGuild().getIconUrl())
                            .addField("**Old Channel**:", oldChannel.getName(), true)
                            .addField("**New channel**:", newChannel.getName(), true).setTimestamp(Instant.now())
                            .queue();
                    } else if (event instanceof GuildUpdateSystemChannelEvent) {
                        GuildChannel oldChannel = getModifiedChannel(event, false);
                        GuildChannel newChannel = getModifiedChannel(event, true);

                        MessageFactory.makeEmbeddedMessage(tc, new Color(120, 120, 120))
                            .setAuthor("System Channel was modified", null, event.getGuild().getIconUrl())
                            .addField("**Old Channel**:", oldChannel.getName(), true)
                            .addField("**New channel**:", newChannel.getName(), true).setTimestamp(Instant.now())
                            .queue();
                    } else if (event instanceof GuildUpdateBoostCountEvent e) {
                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 0, 255))
                            .setAuthor("Boost count was updated", null, event.getGuild().getIconUrl())
                            .addField("**Old Boost count**:", String.valueOf(e.getOldBoostCount()), true)
                            .addField("**New Boost count**:", String.valueOf(e.getNewBoostCount()), true)
                            .setTimestamp(Instant.now()).queue();
                    } else if (event instanceof GuildUpdateBoostTierEvent e) {
                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 0, 255))
                            .setAuthor("Boost **tier** was updated", null, event.getGuild().getIconUrl())
                            .addField("Old Boost **Tier**:", String.valueOf(e.getOldBoostTier()), true)
                            .addField("New Boost **Tier**:", String.valueOf(e.getNewBoostTier()), true)
                            .setTimestamp(Instant.now()).queue();
                    } else if (event instanceof GuildMemberJoinEvent e) {

                        if (checkAccountAge(e)) {
                            if (transformer.getUserAlertsChannelId() != 0
                                && event.getGuild().getTextChannelById(transformer.getUserAlertsChannelId()) != null) {
                                MessageFactory
                                    .makeEmbeddedMessage(
                                        event.getGuild().getTextChannelById(transformer.getUserAlertsChannelId()))
                                    .setThumbnail(e.getUser().getEffectiveAvatarUrl())
                                    .setDescription(
                                        "User found with an *VERY* new account!!!\n\n" + e.getUser().getName() + "#"
                                            + e.getUser().getDiscriminator() + "\n" + "**Created on**: "
                                            + e.getUser().getTimeCreated()
                                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                                            + "\n**User ID**: ``" + e.getUser().getId() + "``")
                                    .queue();
                            }
                        }
                    } else if (event instanceof GuildMemberRoleAddEvent e) {
                        StringBuilder sb = new StringBuilder();
                        for (Role role : e.getRoles()) {
                            sb.append("\n - **").append(role.getName()).append("**");
                        }
                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 129, 31))
                            .setAuthor("Roles were added to member!", null, e.getUser().getEffectiveAvatarUrl())
                            .setDescription("**Member**: " + e.getUser().getAsMention() + "\n" + "**User**: "
                                + e.getUser().getName() + "#" + e.getUser().getDiscriminator() + "\n"
                                + "\n**Roles given**: " + sb)
                            .setFooter("UserID: " + e.getUser().getId()).setTimestamp(Instant.now()).queue();
                    } else if (event instanceof GuildMemberRoleRemoveEvent e) {
                        StringBuilder sb = new StringBuilder();
                        for (Role role : e.getRoles()) {
                            sb.append("\n - **").append(role.getName()).append("**");
                        }
                        MessageFactory.makeEmbeddedMessage(tc, new Color(92, 135, 186))
                            .setAuthor("Roles where removed from member!", null, e.getUser().getEffectiveAvatarUrl())
                            .setDescription("**Member**: " + e.getUser().getAsMention() + "\n" + "**User**: "
                                + e.getUser().getName() + "#" + e.getUser().getDiscriminator() + "\n"
                                + "\n**Roles removed**: " + sb)
                            .setFooter("UserID: " + e.getUser().getId()).setTimestamp(Instant.now()).queue();
                    } else if (event instanceof GuildMemberUpdateNicknameEvent e) {
                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 195, 0))
                            .setAuthor("User nick was changed!", null, e.getUser().getEffectiveAvatarUrl())
                            .setDescription("**Member**: " + e.getUser().getAsMention() + "\n" + "**User**: "
                                + e.getUser().getName() + "#" + e.getUser().getDiscriminator() + "\n"
                                + "**Old name**: ``" + e.getOldNickname() + "``\n" + "**New name**: ``"
                                + e.getNewNickname() + "``")
                            .setFooter("UserID: " + e.getUser().getId()).setTimestamp(Instant.now()).queue();
                    } else if (event instanceof GuildVoiceJoinEvent e) {

                        MessageFactory.makeEmbeddedMessage(tc, new Color(28, 255, 0))
                            .setAuthor(e.getMember().getEffectiveName() + " joined a voice channel!", null,
                                e.getMember().getUser().getEffectiveAvatarUrl())
                            .setDescription("**Member**: " + e.getMember().getUser().getAsMention() + "\n"
                                + "**User**: " + e.getMember().getUser().getName() + "#"
                                + e.getMember().getUser().getDiscriminator() + "\n"
                                + "**Joined channel**: \uD83D\uDD08 " + e.getChannelJoined().getName())
                            .setFooter("UserID: " + e.getMember().getUser().getId()).setTimestamp(Instant.now())
                            .queue();
                    } else if (event instanceof GuildVoiceLeaveEvent e) {

                        MessageFactory.makeEmbeddedMessage(tc, new Color(255, 11, 0))
                            .setAuthor(e.getMember().getEffectiveName() + " left a voice channel!", null,
                                e.getMember().getUser().getEffectiveAvatarUrl())
                            .setDescription("**Member**: " + e.getMember().getUser().getAsMention() + "\n"
                                + "**User**: " + e.getMember().getUser().getName() + "#"
                                + e.getMember().getUser().getDiscriminator() + "\n"
                                + "**Left channel**: \uD83D\uDD07 " + e.getChannelLeft().getName())
                            .setFooter("UserID: " + e.getMember().getUser().getId()).setTimestamp(Instant.now())
                            .queue();
                    } else if (event instanceof GuildVoiceMoveEvent e) {
                        MessageFactory.makeEmbeddedMessage(tc, new Color(156, 0, 255))
                            .setAuthor(e.getMember().getEffectiveName() + " moved voice channels!", null,
                                e.getMember().getUser().getEffectiveAvatarUrl())
                            .setDescription("**Member**: " + e.getMember().getUser().getAsMention() + "\n"
                                + "**User**: " + e.getMember().getUser().getName() + "#"
                                + e.getMember().getUser().getDiscriminator() + "\n"
                                + "**Joined channel**: \uD83D\uDD08 " + e.getChannelJoined().getName() + "\n"
                                + "**Left channel**: \uD83D\uDD07 " + e.getChannelLeft().getName())
                            .setFooter("UserID: " + e.getMember().getUser().getId()).setTimestamp(Instant.now())
                            .queue();
                    }
                }

            }

        }
    }

    public void onJoinLogsEvent(GenericGuildEvent event) {
        GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire,
            event.getGuild());

        if (transformer.getJoinLogs() != 0) {
            TextChannel tc = avaire.getShardManager().getTextChannelById(transformer.getJoinLogs());
            if (event instanceof GuildMemberJoinEvent e) {
                MessageFactory.makeEmbeddedMessage(tc, new Color(77, 224, 102))
                    .setAuthor("Member joined the server!", null, e.getUser().getEffectiveAvatarUrl())
                    .setDescription("**Member**: " + e.getUser().getAsMention() + "\n" + "**User**: "
                        + e.getUser().getName() + "#" + e.getUser().getDiscriminator() + "\n"
                        + "**Account Age**: "
                        + e.getUser().getTimeCreated()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setFooter("UserID: " + e.getUser().getId()).setTimestamp(Instant.now()).queue();
            } else if (event instanceof GuildMemberRemoveEvent e) {

                MessageFactory.makeEmbeddedMessage(tc, new Color(255, 67, 65))
                    .setAuthor("Member left the server!", null, e.getUser().getEffectiveAvatarUrl())
                    .setDescription("**Member**: " + e.getUser().getAsMention() + "\n" + "**User**: "
                        + e.getUser().getName() + "#" + e.getUser().getDiscriminator() + "\n"
                        + "**Account Age**: "
                        + e.getUser().getTimeCreated()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setFooter("UserID: " + e.getUser().getId()).setTimestamp(Instant.now()).queue();
            }
        }
    }

    private boolean checkAccountAge(GuildMemberJoinEvent event) {
        return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis()
            - event.getMember().getUser().getTimeCreated().toInstant().toEpochMilli()) < 60;
    }

    private void messageUpdateEvent(MessageUpdateEvent event, TextChannel tc) {
        if (!event.isFromGuild()) return;
        MessageCache cache = MessageCache.getCache(event.getGuild().getIdLong());
        if (cache.isInCache(event.getMessage())) {
            Message newMessage = event.getMessage();
            CachedMessage oldMessage = cache.get(event.getMessageIdLong());
            MessageChannel channel = event.getChannel();
            String oldContent = oldMessage.getContentRaw();
            String newContent = newMessage.getContentRaw();
            Guild guild = event.getGuild();

            if (newMessage.getAuthor().isBot())
                return;
            if (newContent.length() >= 2000)
                newContent = newContent.substring(0, 1500) + " **...**";
            if (oldContent.length() >= 2000)
                newContent = newContent.substring(0, 1500) + " **...**";

            if ((newMessage.isPinned() && !oldMessage.isPinned()) || (!newMessage.isPinned() && oldMessage.isPinned())
                || (oldContent.equals(newContent)
                && oldMessage.getEmbedList().size() == newMessage.getEmbeds().size())) {
                if (!oldMessage.isPinned()) {
                    tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc)
                        .setAuthor("A message was pinned", newMessage.getJumpUrl(), guild.getIconUrl())
                        .setDescription("**Message sent by**: " + newMessage.getAuthor().getAsMention()
                            + "\n**Sent In**: " + channel.getAsMention()
                            + "\n**Sent On**: "
                            + newMessage.getTimeCreated()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                            + "\n**[Pinned message](:jumpurl)**")
                        .setColor(new Color(211, 255, 0)).setThumbnail(oldMessage.getAttachment())
                        .setTimestamp(Instant.now()).set("jumpurl", newMessage.getJumpUrl()).buildEmbed()).queue();
                } else {
                    tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc)
                        .setAuthor("A message was unpinned", newMessage.getJumpUrl(), guild.getIconUrl())
                        .setDescription("**Message sent by**: " + newMessage.getAuthor().getAsMention()
                            + "\n**Sent In**: " + channel.getAsMention()
                            + "\n**Sent On**: "
                            + newMessage.getTimeCreated()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                            + "\n**[Unpinned message](:jumpurl)**")
                        .setColor(new Color(255, 61, 0)).setThumbnail(oldMessage.getAttachment())
                        .setTimestamp(Instant.now()).set("jumpurl", newMessage.getJumpUrl()).buildEmbed()).queue();
                }
            } else {
                tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc)
                    .setAuthor("A message was edited", newMessage.getJumpUrl(),
                        newMessage.getAuthor().getEffectiveAvatarUrl())
                    .setDescription("**Author**: " + newMessage.getAuthor().getAsMention() + "\n**Sent In**: "
                        + channel.getAsMention() + "\n**Sent On**: "
                        + newMessage.getTimeCreated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                        + "\n\n**Message Content Before**:\n" + oldContent + "\n\n**Message Content After**:\n"
                        + newContent)
                    .setColor(new Color(0, 255, 171)).setThumbnail(oldMessage.getAttachment())
                    .setTimestamp(Instant.now()).buildEmbed()).queue();
            }

            cache.update(oldMessage, new CachedMessage(newMessage));

        }
    }

    private void messageDeleteEvent(MessageDeleteEvent event, TextChannel tc) {
        if (event.getChannel().getType().equals(ChannelType.TEXT)) {
            MessageCache cache = MessageCache.getCache(event.getGuild());

            if (cache.isInCache(event.getMessageIdLong())) {
                CachedMessage message = cache.get(event.getMessageIdLong());
                String content = message.getContentRaw();

                if (tc != null) {
                    if (message.getAuthor().isBot())
                        return;
                    if (content.length() >= 1500)
                        content = content.substring(0, 1500) + " **...**";

                    PlaceholderMessage placeHolderMessage = MessageFactory.makeEmbeddedMessage(tc)
                        .setAuthor("A message was deleted", null, message.getAuthor().getGetEffectiveAvatarUrl())
                        .setDescription("**Author**: " + message.getAuthor().getAsMention() + "\n**Sent In**: "
                            + event.getChannel().getAsMention() + "\n**Sent On**: "
                            + message.getTimeCreated()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                            + "\n\n**Message Content**:\n" + content)
                        .setColor(new Color(255, 0, 0)).setTimestamp(Instant.now());

                    if (message.getAttachment() != null) {
                        placeHolderMessage.setImage(getImageFromAttachment(message.getAttachment()));
                    }

                    tc.sendMessageEmbeds(placeHolderMessage.buildEmbed()).queue();
                    cache.remove(message);
                }
            }
        }
    }

    private String getImageFromAttachment(String attachment) {
        if (attachment.endsWith(".png") || attachment.endsWith(".jpeg") || attachment.endsWith(".img")) {
            return attachment;
        }
        return null;
    }

    private GuildChannel getModifiedChannel(GenericGuildEvent event, boolean newChannel) {
        if (event instanceof GuildUpdateAfkChannelEvent) {
            if (newChannel) {
                return ((GuildUpdateAfkChannelEvent) event).getNewAfkChannel();
            } else {
                return ((GuildUpdateAfkChannelEvent) event).getOldAfkChannel();
            }
        } else if (event instanceof GuildUpdateSystemChannelEvent) {
            if (newChannel) {
                return ((GuildUpdateSystemChannelEvent) event).getNewSystemChannel();
            } else {
                return ((GuildUpdateSystemChannelEvent) event).getOldSystemChannel();
            }
        }
        return null;
    }

}
