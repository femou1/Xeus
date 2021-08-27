package com.pinewoodbuilders.commands.pinewood;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.CheckPermissionUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import net.dv8tion.jda.api.entities.GuildChannel;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EventRequestCommand extends Command {

    public EventRequestCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Event Request Command";
    }

    @Override
    public String getDescription() {
        return "Allows you to request to schedule an event for pinewood groups.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Start the command, questions for the event will be asked."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command` - Start the command, questions for the event will be asked.",
            "`:command set-channel #channel/722805575053738034` - Set the channel mentioned or the ID to the id"
        );
    }


    @Override
    public List<String> getTriggers() {
        return Arrays.asList("event-request", "ev");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.MISCELLANEOUS
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isModOrHigher"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            context.makeError("Something went wrong loading the guild settings. Please try again later.").queue();
            return false;
        }
        if (args.length > 0) {
            if (CheckPermissionUtil.getPermissionLevel(context).getLevel() >= CheckPermissionUtil.GuildPermissionCheckType.MANAGER.getLevel()) {
                switch (args[0]) {
                    case "set-channel": {
                        if (args.length == 1) {
                            return sendErrorMessage(context, "This comand requires a second argument to work. Either mention a channel. Or say the channel ID");
                        }

                        GuildChannel c = MentionableUtil.getChannel(context.message, args, 2);
                        if (c != null) {
                            transformer.setPinewoodEventRequestsChannelId(c.getId());
                            return updateRecordInDatabase(context, transformer.getPinewoodEventRequestsChannelId());
                        } else {
                            return sendErrorMessage(context, "Channel could not be found, please try again.");
                        }
                    } case "reset": {
                        if (transformer.getPinewoodEventRequestsChannelId() == null) {
                            return sendErrorMessage(context, "The event requests channel is not set, please set this channel before resetting.");
                        }

                        transformer.setPinewoodEventRequestsChannelId(null);
                        return updateRecordInDatabase(context, null);
                    }
                    default: {
                        return sendErrorMessage(context, "Invalid argument given.");
                    }
                }
            }
        }
        return false;
    }

    private boolean updateRecordInDatabase(CommandMessage context, String pinewoodEventRequestsChannelId) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("event_request_channel", pinewoodEventRequestsChannelId));
            context.makeSuccess("Updated the event request channel!").queue();
        } catch (SQLException exception) {
            context.makeError("Something went wrong when updating the guild settings, please try again later.").queue();
            return false;
        }
        return true;
    }
}
