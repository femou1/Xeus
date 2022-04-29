package com.pinewoodbuilders.commands.evaluations;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.roblox.evaluations.EvaluationStatus;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EvalStatusCommand extends Command {
    public EvalStatusCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Evaluation Status Command";
    }

    @Override
    public String getDescription() {
        return "Command to see your evaluation status.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Get your eval status"
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Get your eval status"
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("evalstatus", "es");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.EVALUATIONS
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "throttle:user,1,3",
            "isPinewoodGuild"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {

        VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification().fetchVerificationWithBackup(context.getAuthor().getId(), true);
        if (verificationEntity == null) {
            return sendErrorMessage(context, "You are not verified. Please verify yourself with `!verify`.");
        }

        EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(verificationEntity.getRobloxId());
        if (status == null) {
            return sendErrorMessage(context, "You have not gotten an evaluation yet, this seems to be an error with the API.");
        }

        PlaceholderMessage builder = context.makeEmbeddedMessage();
        builder.setTitle("Evaluation Status for " + verificationEntity.getRobloxUsername());
        builder.setDescription("""
            **Passed Quiz**: :quizPassed
            **Passed Combat**: :combatPassed
            **Passed Consensus**: :consensusPassed
            **Evaluator**: :evaluator
            """)
            .set("quizPassed", status.passedQuiz() ? "<:yes:694268114803621908>": "<:no:694270050257076304>")
            .set("combatPassed", status.passedCombat() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
            .set("consensusPassed", status.passedConsensus() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
            .set("evaluator", status.getLastEdit() != null ? status.getLastEvaluator() : "None")
            .set("evaluationDate", status.getFirstEvaluation() != null ? status.getFirstEvaluation().diffForHumans(true) : "None")
            .setFooter("Last modification: " + (status.getLastEdit() != null ? status.getLastEdit().diffForHumans(true) : "None"))
            .setColor(status.isPassed() ? MessageType.SUCCESS.getColor() : MessageType.ERROR.getColor())
            .queue();

        if (status.isPassed()) {
            context.makeSuccess("***Congratulations!*** You have passed all evaluations! If you're not yet promoted, an Leadership+ will soon promote you!").queue();
        }

        return false;
    }

    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return null;
        }
    }


}
