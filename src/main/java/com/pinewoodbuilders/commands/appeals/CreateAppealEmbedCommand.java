package com.pinewoodbuilders.commands.appeals;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
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
    public List <String> getMiddleware() {
        return List.of(
            "isValidMGMMember"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, getMissingArgsErrorMessage());
        }

        return switch (args[0]) {
            case "appeal", "a" -> createAppealEmbed(context);
            case "deletion", "d" -> createDeletionEmbed(context);
            default -> sendErrorMessage(context, getMissingArgsErrorMessage());
        };

    }

    private boolean createDeletionEmbed(CommandMessage context) {
        context.getMessageChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Pinewood - Deletion Requests")
                    .setDescription("""
                    Hello and welcome to the Pinewood Data Deletion Request System.
                    We've gotten a few requests to delete all data contained with within Kronos (Points for example).
                    
                    If you'd like to request a deletion of your data, please click on the button below.
                    ***WARNING***: This is a one-time request. If you delete your data, you will not be able to retrieve it.
                    ___WE WILL NOT BE HELD LIABLE FOR ANY POINTS, CREDITS OR OTHER DATA LOST DURING THIS PROCESS.___
                    
                    If you understand the above, you may continue by pressing the button below.                    
                    """)
                    .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                    .setColor(new Color(0, 0, 0))
                    .build()
            )
            .setActionRow(Button.success("start-deletion", "I'd like to delete all my data!")).queue();
        return true;
    }

    private boolean createAppealEmbed(CommandMessage context) {
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

    private String getMissingArgsErrorMessage() {
        return """
                You must provide a argument;
                 - `appeal` - **Creates an appeal embed for the current channel.**
                 - `deletion` - **Creates a deletion request embed for the current channel.**
                """;
    }

}
