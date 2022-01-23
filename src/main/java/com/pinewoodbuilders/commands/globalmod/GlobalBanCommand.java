package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandContainer;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.annotations.ReplaceWith;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/*
@Deprecated
@ForRemoval(deadline = "3.2.0")
*/
@ReplaceWith("GlobalModCommand")
/*@DeprecatedSince("3.1.0")*/
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
    //    context.makeWarning("Reminder that this command is scheduled for removal in `v3.2.0`").queue();
        CommandContainer container = CommandHandler.getCommand(GlobalModCommand.class);
        if (container == null) {
            return sendErrorMessage(context, "Unable to get the Global Moderation Command...");
        }
        context.setI18nCommandPrefix(container);

        List<String> argList = new ArrayList<>();
        argList.add("gb");
        argList.addAll(Arrays.stream(args).collect(Collectors.toList()));

        return container.getCommand().onCommand(context, argList.toArray(String[]::new));
    }



}
