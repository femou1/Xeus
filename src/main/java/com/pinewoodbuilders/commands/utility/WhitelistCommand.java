package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.entities.GuildVoiceState;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WhitelistCommand extends Command {


    public WhitelistCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Whitelist Command";
    }

    @Override
    public String getDescription() {
        return "Whitelist a user to a voicechannel.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Start whitelisting."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Start whitelisting."
        );
    }


    @Override
    public List<String> getTriggers() {
        return Arrays.asList("whitelist", "wl");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.REPORTS
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "isGuildHROrHigher"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeError("Please tell me what you'd like to do. I'm not an AI, so I can't think about what you want :(").queue();
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "add":
                    return addToWhitelist(context);
                case "remove":
                    return removeFromWhitelist(context);
                case "add-all":
                    return whitelistAndAddAll(context);
                case "enable":
                    return enableWhitelist(context);
                case "disable":
                    return disableWhitelist(context);
                default:
                    return sendErrorMessage(context, "Please enter in a correct argument.");
            }
        }
        return false;
    }

    private boolean disableWhitelist(CommandMessage context) {
        GuildVoiceState state = context.getMember().getVoiceState();
        if (state == null) {
            context.makeError("State does not exist.").queue();
            return false;
        }

        if (!state.inAudioChannel()) {
            context.makeError("You are not in a voice channel, please join one to enable the whitelist in it.").queue();
            return false;
        }

        context.makeSuccess("Disabled whitelist!").queue();
        return avaire.getVoiceWhitelistManager().removeVoiceChannelFromWhitelists(state.getChannel());
    }

    private boolean enableWhitelist(CommandMessage context) {
        GuildVoiceState state = context.getMember().getVoiceState();
        if (state == null) {
            context.makeError("State does not exist.").queue();
            return false;
        }

        if (!state.inAudioChannel()) {
            context.makeError("You are not in a voice channel, please join one to enable the whitelist in it.").queue();
            return false;
        }
        context.makeSuccess("Enabled whitelist!").queue();
        return avaire.getVoiceWhitelistManager().addVoiceChannelToWhitelists(state.getChannel());
    }

    private boolean whitelistAndAddAll(CommandMessage context) {
        GuildVoiceState state = context.getMember().getVoiceState();
        if (state == null) {
            context.makeError("State does not exist.").queue();
            return false;
        }

        if (!state.inAudioChannel()) {
            context.makeError("You are not in a voice channel, please join one to enable the whitelist and add every person in it to the whitelist.").queue();
            return false;
        }

        return avaire.getVoiceWhitelistManager().addAllUsersInVcToWhitelistAndEnable(context);
    }

    private boolean removeFromWhitelist(CommandMessage context) {
        if (context.message.getMentions().getMembers().size() < 1) {
            context.makeError("Please **mention** who you'd like to whitelist.").queue();
            return false;
        }
        if (context.message.getMentions().getMembers().size() > 1) {
            context.makeError("Please **mention** only ***one*** member you'd like to remove from the whitelist.").queue();
            return false;
        }

        return avaire.getVoiceWhitelistManager().removeUserFromWhitelist(context, context.message.getMentions().getMembers().get(0));
    }

    private boolean addToWhitelist(CommandMessage context) {
        if (context.message.getMentions().getMembers().size() < 1) {
            context.makeError("Please **mention** who you'd like to whitelist.").queue();
            return false;
        }
        if (context.message.getMentions().getMembers().size() > 1) {
            context.makeError("Please **mention** only ***one*** member you'd like to whitelist.").queue();
            return false;
        }

        return avaire.getVoiceWhitelistManager().addUserToWhitelist(context, context.message.getMentions().getMembers().get(0));
    }

}
