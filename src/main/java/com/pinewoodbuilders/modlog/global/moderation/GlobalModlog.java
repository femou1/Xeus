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

package com.pinewoodbuilders.modlog.global.moderation;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogAction;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogType;
import com.pinewoodbuilders.utilities.RestActionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;

public class GlobalModlog {

    /**
     * Logs an action to the modlog channel for the given context.
     *
     * @param avaire  The main Xeus application instance.
     * @param context The command context the modlog action is occurring in.
     * @param action  The action that should be logged to the modlog.
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     * otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, CommandMessage context, GlobalModlogAction action) {
        return log(avaire, context.getGuildSettingsTransformer(), action);
    }

    /**
     * Logs an action to the modlog channel for the given message.
     *
     * @param avaire  The main Xeus application instance.
     * @param transformer The message that triggered the modlog action.
     * @param action  The action that should be logged to the modlog.
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     * otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, GuildSettingsTransformer transformer, GlobalModlogAction action) {
        if (transformer != null) {
            return log(avaire, transformer.getGlobalSettings(), action);
        }
        return null;
    }

    /**
     * Logs an action to the modlog channel for the given guild
     * using the guild transformer, and the modlog action.
     *
     * @param avaire      The main Xeus application instance.
     * @param transformer The guild transformer containing all the guild settings used in the modlog action.
     * @param action      The action that should be logged to the modlog.
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     * otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, GlobalSettingsTransformer transformer, GlobalModlogAction action) {
        if (transformer.getGlobalModlogChannel() == null) {
            return null;
        }

        TextChannel channel = avaire.getShardManager().getTextChannelById(transformer.getGlobalModlogChannel());
        if (channel == null) {
            return null;
        }

        if (!channel.canTalk()) {
            return null;
        }

        transformer.setGlobalModlogCase(transformer.getGlobalModlogCase() + 1);

        String[] split;
        EmbedBuilder builder = MessageFactory.createEmbeddedBuilder()
            .setTitle(I18n.format("{0} {1} - {2} | Case #{3}",
                action.getType().getName(transformer),
                action.getType().getEmote(),
                transformer.getMainGroupName(),
                transformer.getGlobalModlogCase()
            ))
            .setColor(action.getType().getColor())
            .setTimestamp(Instant.now());

        switch (action.getType()) {
            case GLOBAL_WARN:
            case GLOBAL_KICK:
            case GLOBAL_BAN:
            case GLOBAL_UNBAN:
            case GLOBAL_UNMUTE:
            case GLOBAL_WATCH:
            case GLOBAL_UN_WATCH:
                builder
                    .addField("User", action.getStringifiedTarget(), true)
                    .addField("Moderator", action.getStringifiedModerator(), true)
                    .addField("Reason", formatReason(transformer, action.getMessage()), false);
                break;

            case GLOBAL_MUTE:
            case GLOBAL_TEMP_MUTE:
            case GLOBAL_TEMP_BAN:
            case GLOBAL_TEMP_WATCH:
                //noinspection ConstantConditions
                split = action.getMessage().split("\n");
                builder
                    .addField("User", action.getStringifiedTarget(), true)
                    .addField("Moderator", action.getStringifiedModerator(), true);

                if (split[0].length() > 0) {
                    builder.addField("Expires At", split[0], true);
                }

                builder.addField("Reason", formatReason(transformer, String.join("\n",
                    Arrays.copyOfRange(split, 1, split.length)
                )), false);
                break;
            case GLOBAL_PARDON:
                //noinspection ConstantConditions
                split = action.getMessage().split("\n");
                String[] modlogParts = split[0].split(":");
                builder
                    .addField("Pardoned Case ID", I18n.format("#[{0}](https://discordapp.com/channels/{1}/{2}/{3})",
                        modlogParts[0], channel.getGuild().getId(), channel.getId(), modlogParts[1]
                    ), true)
                    .addField("Moderator", action.getStringifiedModerator(), true)
                    .addField("Reason", formatReason(transformer, String.join("\n",
                        Arrays.copyOfRange(split, 1, split.length)
                    )), false);

                action.setMessage(String.join("\n",
                    Arrays.copyOfRange(split, 1, split.length)
                ));
                break;
        }

        /*avaire.getEventEmitter().push(new GlobalModlogActionEvent(
            guild.getJDA(), action, transformer.getGlobalModlogCase()
        ));*/

        channel.sendMessageEmbeds(builder.build()).queue(success -> {
            try {
                avaire.getDatabase().newQueryBuilder(Constants.GLOBAL_SETTINGS_TABLE)
                    .where("main_group_id", transformer.getMainGroupId())
                    .update(statement -> {
                        statement.set("global_modlog_case", transformer.getGlobalModlogCase());
                    });

                logActionToTheDatabase(avaire, transformer.getMainGroupId(), action, success, transformer.getGlobalModlogCase());
            } catch (SQLException ignored) {
                //
            }
        }, RestActionUtil.ignore);

        return "" + transformer.getGlobalModlogCase();
    }

    private static void logActionToTheDatabase(Xeus avaire, long mgi, GlobalModlogAction action, Message message, int modlogCase) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.MGM_LOG_TABLE_NAME)
                .useAsync(true)
                .insert(statement -> {
                    statement.set("modlogCase", modlogCase);
                    statement.set("type", action.getType().getId());
                    statement.set("mgi", mgi);
                    statement.set("user_id", action.getModerator().getId());

                    if (action.getTarget() != null) {
                        statement.set("target_id", action.getTarget().getId());
                    }

                    if (message != null) {
                        statement.set("message_id", message.getId());
                    }

                    statement.set("reason", formatReason(null, action.getMessage()), true);
                });
        } catch (SQLException ignored) {
            ignored.printStackTrace();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static String formatReason(@Nullable GlobalSettingsTransformer transformer, String reason) {
        if (reason == null || reason.trim().equalsIgnoreCase("No reason was given.")) {
            if (transformer != null) {
                //CommandContainer command = CommandHandler.getCommand(GlobalModlogReasonCommand.class);
                /*String prefix = transformer.getPrefixes().getOrDefault(
                    command.getCategory().getName(), command.getDefaultPrefix()
                );*/

                return String.format(
                    "Moderator do `%sreason %s <reason>`",
                    /*prefix,*/"<prefix>", transformer.getGlobalModlogCase()
                );
            }
            return null;
        }
        return reason;
    }

    /**
     * Notifies the given user about a modlog action by DMing them
     * a message, if the user have DM messages disabled, nothing
     * will be sent and the method will fail silently.
     *
     * @param user   The user that should be notified about a modlog action.
     * @param guild  The guild that the modlog action happened in.
     * @param action The modlog action that the user should be notified of.
     * @param caseId The case ID that is attached to the modlog action.
     */
    public static void notifyUser(User user, GlobalSettingsTransformer guild, GlobalModlogAction action, @Nullable String caseId) {
        String type = action.getType().getNotifyName(guild);
        if (type == null || user.isBot()) {
            return;
        }

        user.openPrivateChannel().queue(channel -> {
            EmbedBuilder message = MessageFactory.createEmbeddedBuilder()
                .setColor(MessageType.MGM_PINEWOOD.getColor())
                .setDescription(String.format("%s You have been **%s** %s %s",
                    action.getType().getEmote(),
                    type,
                    action.getType().equals(GlobalModlogType.GLOBAL_WARN)
                        ? "in" : "from",
                    guild.getMainGroupName()
                ))
                //.addField("Moderator", action.getModerator().getName() + "#" + action.getModerator().getDiscriminator(), true)
                .addField("Reason", action.getMessage(), true)
                .setTimestamp(Instant.now());

            if (caseId != null) {
                message.setFooter("Case ID #" + caseId, null);
            }

            channel.sendMessageEmbeds(message.build()).queue(null, RestActionUtil.ignore);
        }, RestActionUtil.ignore);
    }
}
