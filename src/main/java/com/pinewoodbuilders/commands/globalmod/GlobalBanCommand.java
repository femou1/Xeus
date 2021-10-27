package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.annotations.DeprecatedSince;
import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.annotations.ReplaceWith;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
@Deprecated
@ForRemoval(deadline = "3.2.0")
@ReplaceWith("GlobalModCommand")
@DeprecatedSince("3.1.0")
public class GlobalBanCommand extends Command {



    public GlobalBanCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Global Ban Command";
    }

    @Override
    public String getDescription() {
        return "Ban member globally.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Ban a member globally.");
    }

    @Override
    public List<String> getExampleUsage(@Nullable Message message) {
        return Collections.singletonList("`:command` - Ban a member globally.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("global-ban");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        context.makeWarning("Command moved to !global-mod (!gb)...\n`!gm gb d <id> <true/false> <reason>` | `!global-mod global-ban discord <id> <true/false> <reason>").queue();
        return false;
    }



}
