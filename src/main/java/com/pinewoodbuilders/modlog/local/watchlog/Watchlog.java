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

package com.pinewoodbuilders.modlog.local.watchlog;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandContainer;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.onwatch.OnWatchCommand;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.utilities.RestActionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;

public class Watchlog {

    /**
     * Logs an action to the modlog channel for the given context.
     *
     * @param avaire  The main Xeus application instance.
     * @param context The command context the modlog action is occurring in.
     * @param action  The action that should be logged to the modlog.
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     *         otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, CommandMessage context, ModlogAction action) {
        return log(avaire, context.getGuild(), action);
    }

    /**
     * Logs an action to the modlog channel for the given message.
     *
     * @param avaire  The main Xeus application instance.
     * @param message The message that triggered the modlog action.
     * @param action  The action that should be logged to the modlog.
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     *         otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, Message message, ModlogAction action) {
        return log(avaire, message.getGuild(), action);
    }

    /**
     * Logs an action to the modlog channel for the given guild.
     *
     * @param avaire The main Xeus application instance.
     * @param guild  The guild the modlog action should be logged in.
     * @param action The action that should be logged to the modlog.
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     *         otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, Guild guild, ModlogAction action) {
        GuildTransformer transformer = GuildController.fetchGuild(avaire, guild);
        if (transformer != null) {
            return log(avaire, guild, transformer, action, GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guild));
        }
        return null;
    }

    /**
     * Logs an action to the modlog channel for the given guild
     * using the guild transformer, and the modlog action.
     *
     * @param avaire      The main Xeus application instance.
     * @param guild       The guild the modlog action should be logged in.
     * @param transformer The guild transformer containing all the guild settings used in the modlog action.
     * @param action      The action that should be logged to the modlog.
     * @param GuildSettingsTransformer
     * @return Possibly-null, the case ID if the modlog was logged successfully,
     *         otherwise <code>null</code> will be returned.
     */
    @Nullable
    public static String log(Xeus avaire, Guild guild, GuildTransformer transformer, ModlogAction action, GuildSettingsTransformer GuildSettingsTransformer) {
        if (GuildSettingsTransformer.getOnWatchChannel() == 0) {
            return null;
        }

        TextChannel channel = guild.getTextChannelById(GuildSettingsTransformer.getOnWatchChannel());
        if (channel == null) {
            return null;
        }

        if (!channel.canTalk()) {
            return null;
        }

        transformer.setOnWatchCase(transformer.getOnWatchCase() + 1);

        String[] split = null;
        EmbedBuilder builder = MessageFactory.createEmbeddedBuilder()
            .setTitle(I18n.format("{0} {1} | Case #{2}",
                action.getType().getEmote(),
                action.getType().getName(guild),
                transformer.getOnWatchCase()
            ))
            .setColor(action.getType().getColor())
            .setTimestamp(Instant.now());

        switch (action.getType()) {
            case UN_ON_WATCH:
                builder
                    .addField("User", action.getStringifiedTarget(), true)
                    //.addField("Moderator", action.getStringifiedModerator(), true)
                    .addField("Reason", formatReason(transformer, action.getMessage()), false);
                break;

            case ON_WATCH:
            case TEMP_ON_WATCH:
                //noinspection ConstantConditions
                split = action.getMessage().split("\n");
                builder
                    .addField("User", action.getStringifiedTarget(), true)
                    //.addField("Moderator", action.getStringifiedModerator(), true);
                    ;
                if (split[0].length() > 0) {
                    builder.addField("Expires At", split[0], true);
                }

                builder.addField("Reason", formatReason(transformer, String.join("\n",
                    Arrays.copyOfRange(split, 1, split.length)
                )), false);
                break;

        }

        channel.sendMessageEmbeds(builder.build()).queue(success -> {
            try {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                    .where("id", guild.getId())
                    .update(statement -> {
                        statement.set("on_watch_case", transformer.getOnWatchCase());
                    });

                logActionToTheDatabase(avaire, guild, action, success, transformer.getOnWatchCase());
            } catch (SQLException ignored) {
                //
            }
        }, RestActionUtil.ignore);

        return "" + transformer.getOnWatchCase();
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
    public static void notifyUser(User user, Guild guild, ModlogAction action, @Nullable String caseId) {
        String type = action.getType().getNotifyName(guild);
        if (type == null || user.isBot()) {
            return;
        }

        user.openPrivateChannel().queue(channel -> {
            EmbedBuilder message = MessageFactory.createEmbeddedBuilder()
                .setColor(action.getType().getColor())
                .setDescription(String.format("%s You have been **%s** %s %s",
                    action.getType().getEmote(),
                    type,
                    action.getType().equals(ModlogType.ON_WATCH)
                        ? "in" : "from",
                    guild.getName()
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

    private static void logActionToTheDatabase(Xeus avaire, Guild guild, ModlogAction action, Message message, int modlogCase) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.ON_WATCH_LOG_TABLE_NAME)
                .useAsync(true)
                .insert(statement -> {
                    statement.set("modlogCase", modlogCase);
                    statement.set("type", action.getType().getId());
                    statement.set("guild_id", guild.getId());
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
            //
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static String formatReason(@Nullable GuildTransformer transformer, String reason) {
        if (reason == null || reason.trim().equalsIgnoreCase("No reason was given.")) {
            if (transformer != null) {
                CommandContainer command = CommandHandler.getCommand(OnWatchCommand.class);
                String prefix = transformer.getPrefixes().getOrDefault(
                    command.getCategory().getName(), command.getDefaultPrefix()
                );

                return String.format(
                    "Moderator do `%sreason %s <reason>`",
                    prefix, transformer.getOnWatchCase()
                );
            }
            return null;
        }
        return "Please keep in mind that repeating the actions or following a similar line of behaviour that lead to originally gaining the On-Watch status may result to a more severe punishment.\n\n **Punishment Reason**:\n" + reason;
    }
}
