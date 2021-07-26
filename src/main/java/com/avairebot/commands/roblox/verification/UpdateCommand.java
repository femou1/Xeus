package com.avairebot.commands.roblox.verification;

import com.avairebot.AvaIre;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.contracts.verification.VerificationEntity;
import com.avairebot.utilities.MentionableUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UpdateCommand extends Command {

    public UpdateCommand(AvaIre avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Update Command";
    }

    @Override
    public String getDescription() {
        return "Update a user in the discord.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
                "`:command <user>` - Update a user in the discord."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
                "`:command @Stefano#7366`"
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
                "isOfficialPinewoodGuild",
                "isModOrHigher"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("update");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeError("Who/whom would you like to verify?").queue();
            return false;
        }
        User u = MentionableUtil.getUser(context, args);
        if (u != null) {
            Member m = context.guild.getMember(u);
            if (m != null) {
                VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(u.getId());
                if (verificationEntity != null) {
                    return avaire.getRobloxAPIManager().getVerification().verify(context, m);
                } else {
                    context.makeError("This user is not verified in any database. Please ask him/her to verify with `!verify`").queue();
                }
            } else {
                context.makeError("Member not found.").queue();
            }
        } else {
            context.makeError("User not found.").queue();
        }
        return false;
    }
}
