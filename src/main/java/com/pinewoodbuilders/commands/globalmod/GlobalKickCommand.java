package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.annotations.DeprecatedSince;
import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.annotations.ReplaceWith;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
@Deprecated
@ForRemoval(deadline = "3.2.0")
@ReplaceWith("GlobalModCommand")
@DeprecatedSince("3.1.0")
public class GlobalKickCommand extends Command {

    public final ArrayList<String> guilds = new ArrayList<String>() {
        {
            add("495673170565791754"); // Aerospace
            add("438134543837560832"); // PBST
            add("791168471093870622"); // Kronos Dev
            add("371062894315569173"); // Official PB Server
            add("514595433176236078"); // PBQA
            add("436670173777362944"); // PET
            add("505828893576527892"); // MMFA
            add("498476405160673286"); // PBM
            add("572104809973415943"); // TMS
            add("758057400635883580"); // PBOP
            add("669672893730258964"); // PB Dev
        }
    };
    public final HashMap<Guild, Role> role = new HashMap<>();
    private final ArrayList<Guild> guild = new ArrayList<>();

    public GlobalKickCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Global Kick Command";
    }

    @Override
    public String getDescription() {
        return "Kick member globally.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Kick a member globally.");
    }

    @Override
    public List<String> getExampleUsage(@Nullable Message message) {
        return Collections.singletonList("`:command` - Kick a member globally.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("global-kick");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {

        context.makeWarning("Command moved to !global-mod (!gb)...\n`!gm gk <id>` | `!global-mod global-kick <id>").queue();

/*
        if (args.length < 1) {
            context.makeError("Sorry, but you didn't give any member id to globally kick!").queue();
            return false;
        }

        if (args.length == 1) {
            context.makeError("Please supply a reason for the global kick!").queue();
            return true;
        }

        if (args.length < 3) {
            context.makeError("Please supply a reason for the global kick!").queue();
            return true;
        }

        if (guild.size() > 0) {
            guild.clear();
        }
        for (String s : guilds) {
            Guild g = avaire.getShardManager().getGuildById(s);
            if (g != null) {
                guild.add(g);
            }
        }
*/



        return true;
    }

}
