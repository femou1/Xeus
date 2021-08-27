package com.pinewoodbuilders.commands.reports;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.utilities.MentionableUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AddCommand extends Command {

    public AddCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Add User Command";
    }

    @Override
    public String getDescription() {
        return "Add a user to a ticket channel.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Add a user to a ticket!"
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Add a user to the ticket you ran the command in."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("add");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.REPORTS
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            /*"isTicket",*/
            "throttle:user,1,5"
        );
    }

    EnumSet <Permission> allow = EnumSet.of(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE);

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!(isValidTicketChannel(context.getChannel().getName()))) {
            context.makeError("This is NOT a valid ticket channel.").queue();
            return true;
        }
        User u = MentionableUtil.getUser(context, args);
        if (u != null) {
            Member user = context.guild.getMember(u);
            if (user != null) {
                if (context.channel.getPermissionOverride(user) == null) {
                    context.channel.createPermissionOverride(user)
                        .setAllow(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE).queue();
                    context.makeSuccess("Added " + user.getAsMention() + " to the ticket!").queue();
                    return true;
                } else {
                    context.makeError("This user already has a override on this channel (Meaning he/she should already see this channel).").queue();
                    return false;
                }
            } else {
                context.makeError("Failed to find member :slight_frown:").queue();
                return false;
            }
        } else {
            context.makeError("Failed to find user :slight_frown:").queue();
            return false;
        }
    }

    private boolean isValidTicketChannel(String context) {
        return context.startsWith("report-");
    }
}
