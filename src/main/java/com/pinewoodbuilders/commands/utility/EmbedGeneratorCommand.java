package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class EmbedGeneratorCommand extends Command {

    private final HashMap<User, EmbedBuilder> embeds = new HashMap<>();

    public EmbedGeneratorCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Embed Generator Command";
    }

    @Override
    public String getDescription() {
        return "Create an embed in a specified channel, or in the channel you ran the command in.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command [channel]` - Start the embed-generator");
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command`"
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isGuildHROrHigher",
            "throttle:guild,1,5"
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Collections.singletonList(ChannelIdCommand.class);
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.INFORMATION);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("embed-generator", "e-g", "eg");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (context.member.getId().equals("173839105615069184")) {
            /*if (args.length < 1) {
                context.makeError("Please enter in a argument.").queue();
                return false;
            }

            EmbedBuilder eb = null;
            if (embeds.get(context.getAuthor()) == null) {
                eb = new EmbedBuilder();
                embeds.put(context.getAuthor(), eb);
            }

            switch (args[0].toLowerCase()) {
                case "e":
                case "edit":
                    return runEditArgument(context, args, eb);
                case "s":
                case "send":
                    return runSendArgument(context, args);
            }
            return false;*/
            return false;
        } else {
            context.makeError("Command still has to be created...").requestedBy(context).queue();
            return true;
        }
    }

    private boolean runEditArgument(CommandMessage context, String[] args, EmbedBuilder eb) {
        if (args.length < 2) {
            context.makeError("You're missing the thing you want to edit.").queue();
            return false;
        }

        switch (args[1]) {
            case "t":
            case "title":
                eb.setTitle(waitForResponse(context));
        }
        return true;
    }

    private String waitForResponse(CommandMessage context) {
        StringBuilder sb = new StringBuilder();
        avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, event -> event.getAuthor().equals(context.getAuthor()) && event.getChannel().equals(context.getChannel()), p -> {
            sb.append(p.getMessage().getContentRaw());
        });
    return sb.toString();
    }

    private boolean runSendArgument(CommandMessage context, String[] args) {
        return true;
    }

}
