package com.pinewoodbuilders.commands.appeals;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateAppealEmbedCommand extends Command {

    public CreateAppealEmbedCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Create Appeal Embed Command";
    }

    @Override
    public String getDescription() {
        return "Creates an appeal embed for the current channel.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Creates an appeal embed for the current channel");
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("am", "appeal-message");
    }

    @Override
    public List<String> getMiddleware() {
        return List.of(
            "isValidMGMMember"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        context.getMessageChannel().sendMessageEmbeds(
            new EmbedBuilder()
                .setTitle("Pinewood - Appeal System")
                .setDescription("""
                    Hello and welcome to the Pinewood Appeal system.
                    This is the server to appeal your punishment.
                    
                    To start your appeal, click the button below. You will be asked a few questions.
                    If you have any questions, please contact a PIA Member
                    """)
                .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                .setColor(new Color(0, 0, 0))
                .build()
        )
            .setActionRow(Button.success("start-appeal", "I'd like to appeal my punishment!")).queue();
        return true;
    }

}
