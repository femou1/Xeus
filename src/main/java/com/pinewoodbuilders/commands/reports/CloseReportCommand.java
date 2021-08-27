package com.pinewoodbuilders.commands.reports;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CloseReportCommand extends Command {

    public CloseReportCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Close Discord User Report Command";
    }

    @Override
    public String getDescription() {
        return "Close a report channel.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Close a report channel."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Close a report channel."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("close");
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
            "isOfficialPinewoodGuild",
            "throttle:user,1,5"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!context.getGuild().getId().equals("438134543837560832")) {
            return false;
        }
        if (!context.channel.getName().startsWith("report-")) {
            context.makeError("This is NOT a valid report channel.").queue();
            return true;
        }
        String[] topic = context.channel.getTopic().split("(\\s\\|\\s)");
        List<String> roles = context.getGuildTransformer().getReportPermissionRoles();
        if (context.member.hasPermission(Permission.ADMINISTRATOR) || topic[0].equals(context.member.getId()) || checkRoles(context, roles)) {
            context.makeSuccess("Channel will be removed in 30 seconds!").queue(l -> {l.getTextChannel().delete().reason("Channel removal by: " + context.member.getEffectiveName()).queueAfter(30, TimeUnit.SECONDS);});
        } else {
            context.makeError("You're not permitted to remove this channel!").queue();
        }
        return false;
    }

    private boolean checkRoles(CommandMessage context, List<String> roles) {
        List<Role> r = new ArrayList<>();
        for (String i : roles) {
            if (context.getGuild().getRoleById(i) != null) {
                r.add(context.getGuild().getRoleById(i));
            }
        }
        return context.member.getRoles().stream().anyMatch(r::contains);
    }
}
