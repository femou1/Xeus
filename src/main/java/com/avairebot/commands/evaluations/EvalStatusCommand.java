package com.avairebot.commands.evaluations;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.contracts.commands.CommandGroup;
import com.avairebot.contracts.commands.CommandGroups;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.collection.DataRow;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EvalStatusCommand extends Command {
    public EvalStatusCommand(AvaIre avaire) {
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
            "isOfficialPinewoodGuild"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {

        String name = context.member.getEffectiveName();

        try {
        if (!(isValidRobloxUser(name))){
            context.makeError("Your discord name is not a valid roblox name and/or is not in the PBST Group...\nPlease do ``!verify`` to re-verify yourself. (``v!verify`` if the prefix isn't changed").queue();
            return true;
        }

            Collection collection = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", getRobloxId(name)).get();

            if (args.length < 2) {
                if (collection.size() < 1) {
                    context.makeEmbeddedMessage(new Color(255, 0, 0)).setDescription("You have not yet passed anything:\n\n" +
                        "**Passed Quiz**: <:no:694270050257076304>\n" +
                        "**Passed Patrol**: <:no:694270050257076304>\n" +
                        "**Passed Combat**: <:no:694270050257076304>\n\n" +
                        "**Last Evaluator**: ``No evaluation has been given yet.``").queue();
                    return true;
                }

                if (collection.size() > 2) {
                    context.makeError("Something is wrong in the database, there are records with multiple usernames, but the same user id. Please check if this is correct.").queue();
                    return false;
                }
                if (collection.size() == 1) {
                    DataRow row = collection.get(0);
                    Boolean pq = row.getBoolean("passed_quiz");
                    Boolean pp = row.getBoolean("passed_patrol");
                    Boolean pc = row.getBoolean("passed_combat");

                    String passed_quiz = pq ? "<:yes:694268114803621908>" : "<:no:694270050257076304>";
                    String passed_patrol = pp ? "<:yes:694268114803621908>" : "<:no:694270050257076304>";
                    String passed_combat = pc ? "<:yes:694268114803621908>" : "<:no:694270050257076304>";
                    String evaluator = row.getString("evaluator") != null ? row.getString("evaluator") : "Unkown Evaluator";

                    context.makeEmbeddedMessage().setDescription("This user has this information in the database:\n\n" +
                        "**Passed Quiz**: " + passed_quiz + "\n" +
                        "**Passed Patrol**: " + passed_patrol + "\n" +
                        "**Passed Combat**: " + passed_combat + "\n"
                        + (row.getBoolean("passed_combat") && row.getBoolean("passed_patrol") ? "**You have passed all evaluations!**\n\n" : "\n") +
                        "**Last Evaluator**: " + evaluator)
                        .setColor((pq && pp && pc ? new Color(26, 255, 0) : new Color(255, 170, 0))).queue();
                    return true;
                }

            }
        } catch (SQLException e) {
            AvaIre.getLogger().error("ERROR: ", e);
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

    public boolean isValidRobloxUser(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un) != null;
        } catch (Exception e) {
            return false;
        }
    }

}
