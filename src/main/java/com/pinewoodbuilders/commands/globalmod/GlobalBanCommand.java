package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList("isValidMGMMember");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        context.makeWarning("Command moved to !global-mod (!gb)...").queue();
    return false;
    }



}
