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

package com.pinewoodbuilders.chat;

import com.pinewoodbuilders.utilities.StringReplacementUtil;
import net.dv8tion.jda.api.entities.*;

class DefaultPlaceholders {

    static String parse(PlaceholderType type, Object object, String message) {
        switch (type) {
            case ALL:
                if (object instanceof Message jdaMessage && ((Message) object).getChannelType().isGuild()) {
                    return parseGuild(jdaMessage.getGuild(), parseChannel(jdaMessage.getChannel(), parseUser(jdaMessage.getAuthor(), message)));
                }

            case GUILD:
                if (object instanceof Guild guild) {
                    return parseGuild(guild, message);
                }

                if (object instanceof Message && ((Message) object).getChannelType().isGuild()) {
                    return parseGuild(((Message) object).getGuild(), message);
                }
                break;

            case CHANNEL:
                if (object instanceof TextChannel textChannel) {
                    return parseChannel(textChannel, message);
                }

                if (object instanceof ThreadChannel threadChannel) {
                    return parseChannel(threadChannel, message);
                }

                if (object instanceof Message && ((Message) object).getChannelType().equals(ChannelType.TEXT)) {
                    return parseChannel(((Message) object).getChannel().asTextChannel(), message);
                }

                break;

            case USER:
                if (object instanceof User usr) {
                    return parseUser(usr, message);
                }

                if (object instanceof Message msg) {
                    return parseUser(msg.getAuthor(), message);
                }
                break;

            default:
                for (PlaceholderType placeholderType : PlaceholderType.values()) {
                    message = parse(placeholderType, object, message);
                }
                return message;
        }

        return message;
    }

    static String toGuild(Message message, String string) {
        if (!message.getChannelType().isGuild() || string == null) return string;
        return parseGuild(message.getGuild(), string);
    }

    private static String parseGuild(Guild guild, String message) {
        return StringReplacementUtil.replaceAll(message, ":guildid", guild.getId());
    }

    static String toChannel(Message message, String string) {
        if (string == null) return null;
        return parseChannel(message.getChannel(), string);
    }

    private static String parseChannel(MessageChannel channel, String message) {
        message = StringReplacementUtil.replaceAll(message, ":channelname", channel.getName());
        message = StringReplacementUtil.replaceAll(message, ":channelid", channel.getId());
        message = StringReplacementUtil.replaceAll(message, ":channel", channel.getAsMention());

        return message;
    }

    static String toUser(Message message, String string) {
        if (string == null) return null;
        return parseUser(message.getAuthor(), string);
    }

    private static String parseUser(User author, String message) {
        message = StringReplacementUtil.replaceAll(message, ":username", author.getName());
        message = StringReplacementUtil.replaceAll(message, ":userid", author.getId());
        message = StringReplacementUtil.replaceAll(message, ":user", author.getAsMention());

        return message;
    }
}
