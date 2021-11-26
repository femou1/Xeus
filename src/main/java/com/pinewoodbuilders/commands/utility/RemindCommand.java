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

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.SimplePaginator;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.database.controllers.RemindersController;
import com.pinewoodbuilders.database.transformers.RemindersTransformer;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.NumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemindCommand extends Command {
    private static final Logger log = LoggerFactory.getLogger(RemindCommand.class);

    public RemindCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Remind Command";
    }

    @Override
    public String getDescription() {
        return "Reminds you of something after a certain amount of time.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command me <time> <message>`\n- Reminds you about the message after the time is up in a DM.",
            "`:command here <time> <message>`\n- Reminds you about the message after the time is up in the channel the command was used in.",
            "`:command list <page number>`\n - showing up to 10 at a time, with the message, where\n" + "the user will received the message(channel or DM), and when they will receive the message"
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command me 25m Something` - Reminds you about something after 25 minutes.",
            "`:command me 2h30m9s Stuff` - Reminds you about stuff after 2 hours, 30 minutes, and 9 seconds.",
            "`:command here 30m Potato` - Reminds you about Potato in 30 minutes in the current channel.",
            "`:command list 2` - List all pending reminders on page 2"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("remindme", "remind");
    }

    @Override
    public List<String> getMiddleware() {
        return Collections.singletonList("throttle:user,2,60");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "type");
        }

        if(args[0].equalsIgnoreCase("list"))
        {
            return sendReminderList(context,args);
        }
        else if(args[0].equalsIgnoreCase("delete"))
        {
            return deleteReminder(context,args);
        }

        if (!(args[0].equalsIgnoreCase("here") || args[0].equalsIgnoreCase("me"))) {
            return sendErrorMessage(context, context.i18n("errors.invalidMeHere"));
        }



        boolean respondInDM = args[0].equalsIgnoreCase("me");
        if (args.length == 1) {
            return sendErrorMessage(context, "errors.missingArgument", "time");
        }

        final Carbon time = parseTime(args[1]);
        if (time == null) {
            return sendErrorMessage(context, "errors.invalidProperty", "time", "time format");
        }

        if (args.length == 2) {
            return sendErrorMessage(context, "errors.missingArgument", "message");
        }

        handleReminderMessage(
            context,
            String.join(" ", Arrays.copyOfRange(args, 2, args.length)),
            time,
            respondInDM);

        context.makeInfo("Alright :user, in `:time` I'll remind you about \n```:message```")
            .set("time", time.diffForHumans(true))
            .set("message", String.join(" ", Arrays.copyOfRange(args, 2, args.length)))
            .queue();

        return true;
    }

    private boolean deleteReminder(CommandMessage context, String[] args)
    {
        int id = Integer.parseInt(args[1]);
        boolean reminderExists = false;
        RemindersTransformer reminders = RemindersController.fetchPendingReminders(avaire, context.getAuthor().getIdLong());
        for (RemindersTransformer.Reminder reminder: reminders.getReminders())
        {
            if(reminder.getId() == id && reminder.getUserId() == context.getAuthor().getIdLong())
            {
                reminderExists = true;
                break ;
            }
        }
        if(!reminderExists)
        {
            return sendErrorMessage(context,context.i18n("errors.notFound",id));
        }
        try
        {
            int deleted = avaire.getDatabase().newQueryBuilder(Constants.REMINDERS_TABLE_NAME)
                .where("id",id)
                .andWhere("user_id",context.getAuthor().getIdLong())
                .delete();
            if(deleted == 1)
            {
                RemindersController.cache.invalidate(context.getAuthor().getIdLong());
                context.makeSuccess(context.i18n("deletedReminder",id));
            }
            else
            {
                return sendErrorMessage(context,context.i18n("errors.notFound",id));
            }
            return true;
        }
        catch (SQLException e)
        {
            log.debug(e.getMessage());
        }
        return true;
    }

    private boolean sendReminderList(CommandMessage context, String[] args)
    {
        RemindersTransformer transformer = RemindersController.fetchPendingReminders(avaire, context.getAuthor().getIdLong());
        List<RemindersTransformer.Reminder> reminders = transformer.getReminders();
        ArrayList <String> reminderList = new ArrayList<String>();
        for (RemindersTransformer.Reminder reminder: reminders)
        {
            StringBuilder builder = new StringBuilder();
            builder.append("ID:").append( reminder.getId()).append("\n");
            builder.append("Message: ")
                .append(reminder.getMessage())
                .append("\n")
                .append("Location:");
            if(reminder.getChannelId() == null || reminder.getChannelId().isEmpty())
            {
                builder.append("DM");
            }
            else
            {
                builder.append(context.getGuild()
                    .getTextChannelById(reminder.getChannelId())
                    .getAsMention());
            }
            builder.append("\n");
            builder.append("Remaining Time:")
                .append(reminder.getExpirationDate().diffForHumans(true));
            reminderList.add(builder.toString());
        }
        SimplePaginator <String> paginator = new SimplePaginator<String>(reminderList,10);
        if (args.length > 1) {
            paginator.setCurrentPage(NumberUtil.parseInt(args[1], 1));
        }
        List<String> messages = new ArrayList<>();
        paginator.forEach((index, key, val) -> messages.add(val));

        context.makeInfo(":reminders\n\n:paginator")
            .setTitle(context.i18n("title"))
            .set("reminders",String.join("\n",messages))
            .set("paginator", paginator.generateFooter(context.getGuild(), generateCommandTrigger(context.getMessage())))
            .queue();
        return false;

    }


    private void handleReminderMessage(CommandMessage context, String message, Carbon time, boolean respondInDM)
    {
        try
        {

                avaire.getDatabase().newQueryBuilder(Constants.REMINDERS_TABLE_NAME)
                    .insert(statement -> {
                        statement.set("user_id", context.getAuthor().getIdLong());
                        statement.set("message", message, true);
                        statement.set("channel_id", !respondInDM ? context.getMessageChannel().getId() : null);
                        statement.set("stored_at",Carbon.now());
                        statement.set("expires_at", time);
                    });

        }
        catch(SQLException e)
        {
            log.error("Something went wrong while a use was trying to store a reminder: {}", e.getMessage(), e);

            sendErrorMessage(context, context.i18n("failedToStoreInfo", e.getMessage()));
        }
    }

    private final Pattern timeRegEx = Pattern.compile("([0-9]+[w|d|h|m|s])");
    private Carbon parseTime(String string) {
        Matcher matcher = timeRegEx.matcher(string);
        if (!matcher.find()) {
            return null;
        }

        Carbon time = Carbon.now().addSecond();
        do {
            String group = matcher.group();

            String type = group.substring(group.length() - 1);
            int timeToAdd = NumberUtil.parseInt(group.substring(0, group.length() - 1));

            switch (type.toLowerCase()) {
                case "w":
                    time.addWeeks(timeToAdd);
                    break;

                case "d":
                    time.addDays(timeToAdd);
                    break;

                case "h":
                    time.addHours(timeToAdd);
                    break;

                case "m":
                    time.addMinutes(timeToAdd);
                    break;

                case "s":
                    time.addSeconds(timeToAdd);
                    break;
            }
        } while (matcher.find());

        return time;
    }
}
