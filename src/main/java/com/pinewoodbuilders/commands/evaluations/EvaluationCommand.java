package com.pinewoodbuilders.commands.evaluations;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.roblox.evaluations.EvaluationStatus;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.menu.Paginator;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.pinewoodbuilders.utilities.JsonReader.readJsonFromUrl;

public class EvaluationCommand extends Command {
    private final Paginator.Builder builder;

    public EvaluationCommand(Xeus avaire) {
        super(avaire);
        builder = new Paginator.Builder()
            .setColumns(1)
            .setFinalAction(m -> {try {m.clearReactions().queue();} catch (PermissionException ignore) {}})
            .setItemsPerPage(10)
            .waitOnSinglePage(false)
            .useNumberedItems(true)
            .showPageNumbers(true)
            .wrapPageEnds(true)
            .setEventWaiter(avaire.getWaiter())
            .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public String getName() {
        return "Evaluation Command";
    }

    @Override
    public String getDescription() {
        return "Commands to manage the evaluations.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <roblox username>` - Get the eval status of a user.",
            "`:command <roblox username> failed/passed quiz/patrol` - Fail/Succeed someone for a Quiz/Patrol"
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command superstefano4` - Get the eval status of **superstefano4**.",
            "`:command Cdsi passed quiz` - Succeed **Csdi** for a Quiz"
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("evaluation", "evals");
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
            "isPinewoodGuild",
            "isGuildHROrHigher",
            "throttle:user,1,3"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeError("Invalid usage of command. Please add the required arguments. (Begin with the roblox " +
                "name)").queue();
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "evaluator" -> returnEvaluatorAction(context, args);
            case "set-quiz-channel", "sqc", "quiz-channel", "qc" -> setQuizChannel(context, args);
            case "questions" -> questionSubMenu(context, args);
            case "kronos-sync" -> runKronosSync(context);
            case "oh-no" -> ohNo(context);
            case "oh-yes" -> ohYes(context);
            default -> evalSystemCommands(context, args);
        };
    }

    private boolean ohYes(CommandMessage context) {
        // Send a json request to the following raw url: https://pastebin.com/raw/Yscb67wh
        // The json will be a list of long values, each long being an roblox user id.
        // Every ID in that list get's checked on their eval status, and if they passed both the quiz and the combat.
        // If they passed both, they get a DM with a message saying they passed the quiz and the combat.
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://pastebin.com/raw/Yscb67wh");

        try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                String body = response.body().string();
                JSONArray array = new JSONArray(body);
                List<Long> ids = array.toList().stream().map(Object::toString).map(Long::parseLong).toList();

                for (Long id : ids) {
                    EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(id);
                    if (status.passedQuiz() && status.passedCombat() && !status.passedConsensus()) {
                        String username = avaire.getRobloxAPIManager().getUserAPI().getUsername(id);
                        context.makeInfo("""
                            **:username**

                            Quiz: **:status**
                            Combat: **:status**

                            Verdict: **N.A.**""")
                            .set("username", username)
                            .set("status", "Passed").queue(
                                message -> {
                                    message.createThreadChannel(username).queue();
                                    message.addReaction("\uD83D\uDC4D").queue(); //
                                    message.addReaction("✋").queue(); //
                                    message.addReaction("\uD83D\uDC4E").queue(); //
                                }
                            );

                    }
                }
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return true;
    }

    private boolean ohNo(CommandMessage context) {
        // Send a json request to the following raw url: https://pastebin.com/raw/Yscb67wh
        // The json will be a list of long values, each long being an roblox user id.
        // Every ID will be checked to see if they have passed all evals, and if they haven't. Put their ID in an array.
        // The array will be sent to the following url: https://pastebin.com/raw/Yscb67wh

        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://pastebin.com/raw/Yscb67wh");

        try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                String body = response.body().string();
                JSONArray array = new JSONArray(body);
                List<Long> ids = array.toList().stream().map(Object::toString).map(Long::parseLong).toList();

                List<Long> failed = new ArrayList <>();

                for (Long id : ids) {
                    EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(id);
                    if (!status.isPassed()) {
                        failed.add(id);
                    }
                }

                System.out.println(failed);
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return true;
    }

    private boolean runKronosSync(CommandMessage context) {

        try {
            Collection collection = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).get();
            context.makeInfo("Syncing `" + collection.size() + "`eval records to Kronos").queue();
            for (DataRow dr : collection) {
                Long robloxId = dr.getLong("roblox_id");
                EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(robloxId);

                if (status == null) {
                    continue;
                }

                avaire.getRobloxAPIManager().getKronosManager().modifyEvalStatus(dr.getLong("roblox_id"), "pbst", status.isPassed());
            }
            context.makeSuccess("Synced data with Kronos!").queue();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean questionSubMenu(CommandMessage context, String[] args) {
        if (args.length < 2) {
            context.makeError("Would you like to `add` or `remove` a question? Or would you like to `list` all questions?").queue();
            return false;
        }

        switch (args[1]) {
            case "add":
                return addQuestionToGuildQuestions(context, args);
            case "remove":
                return removeQuestionFromGuildQuestions(context, args);
            case "list":
                return listQuestions(context, args);
            default:
                context.makeError("Would you like to `add` or `remove` a question? Or would you like to `list` all questions?").queue();
                return false;
        }
    }

    private boolean listQuestions(CommandMessage context, String[] args) {
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("The GuildSettingsTransformer is null, please try again later.").queue();
            return false;
        }

        if (transformer.getEvalQuestions().size() < 1) {
            context.makeError("The questions is are empty or null, please fill the questions for this guild.").queue();
            return false;
        }


        builder.setText("Current questions in the list: ")
            .setItems(transformer.getEvalQuestions())
            .setUsers(context.getAuthor())
            .setColor(context.getGuild().getSelfMember().getColor());

        builder.build().paginate(context.getChannel(), 0);
        return true;
    }

    private boolean addQuestionToGuildQuestions(CommandMessage context, String[] args) {
        if (args.length < 3) {
            context.makeInfo("Run the command again, and make sure you add the question you want to add.").queue();
            return false;
        }
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("The GuildSettingsTransformer is null, please try again later.").queue();
            return false;
        }

        String question = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (transformer.getEvalQuestions().contains(question)) {
            context.makeError("This question already exists in the database.").queue();
            return false;
        }

        transformer.getEvalQuestions().add(question);
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId())
                .update(statement -> statement.set("eval_questions", Xeus.gson.toJson(transformer.getEvalQuestions()), true));
            context.makeSuccess("Added `:question` to the database!").set("question", question).queue();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            context.makeError("Something went wrong adding the question to the database.").queue();
            return false;
        }

        return true;
    }

    private boolean removeQuestionFromGuildQuestions(CommandMessage context, String[] args) {
        if (args.length < 3) {
            context.makeInfo("Run the command again, and make sure you add the question you want to remove.").queue();
            return false;
        }
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("The GuildSettingsTransformer is null, please try again later.").queue();
            return false;
        }

        String question = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (!transformer.getEvalQuestions().contains(question)) {
            context.makeError("This question doesn't exist in the database.").queue();
            return false;
        }

        transformer.getEvalQuestions().remove(question);
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId())
                .update(statement -> statement.set("eval_questions", Xeus.gson.toJson(transformer.getEvalQuestions()), true));
            context.makeSuccess("Removed `:question` from the database!").set("question", question).queue();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            context.makeError("Something went wrong removing the question from the database.").queue();
            return false;
        }
        return true;
    }

    private boolean setQuizChannel(CommandMessage context, String[] args) {
        if (args.length < 2) {
            context.makeError("I'm missing the channel ID, please give me the ID.").queue();
            return false;
        }
        if (!NumberUtil.isNumeric(args[1])) {
            context.makeError("I need a channel ***ID***, not the name or anything else.").queue();
            return false;
        }

        TextChannel tc = context.getGuild().getTextChannelById(args[1]);
        if (tc == null) {
            context.makeError("The ID you gave me is invalid... Are you really this stupid?").queue();
            return false;
        }

        try {
            context.getGuildSettingsTransformer().setEvaluationEvalChannel(Long.parseLong(args[1]));
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId()).update(statement -> {
                statement.set("evaluation_answer_channel", context.getGuildSettingsTransformer().getEvaluationEvalChannel());
            });
            context.makeSuccess("Eval answers channel has been set to :channelName.").set("channelName",
                tc.getAsMention()).queue();

            return true;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            context.makeError("Something went wrong...").queue();
            return false;
        }
    }

    private boolean evalSystemCommands(CommandMessage context, String[] args) {
        if (!isValidRobloxUser(args[0])) {
            context.makeError("This user is not a valid robloxian.").queue();
            return false;
        }
        try {
            Collection collection = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where(
                "roblox_id", getRobloxId(args[0])).get();

            if (args.length < 2) {
                String username = args[0];

                long robloxId = avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(username);
                if (robloxId == 0) {
                    context.makeError("User not found.").queue();
                    return false;
                }

                EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(robloxId);
                if (status == null) {
                    context.makeError("Something went wrong with the database.").queue();
                    return false;
                }

                PlaceholderMessage builder = context.makeEmbeddedMessage();
                builder.setTitle("Evaluation Status for " + username);
                builder.setDescription("""
                        **Passed Quiz**: :quizPassed
                        **Passed Combat**: :combatPassed
                        **Passed Consensus**: :consensusPassed
                        **Passed Everything**: :allPassed
                        
                        **First Eval**: :evaluationDate
                        **Latest Eval**: :lastEvaluation
                        **Evaluator**: :evaluator
                        """)

                    .set("quizPassed", status.passedQuiz() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
                    .set("combatPassed", status.passedCombat() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
                    .set("consensusPassed", status.passedConsensus() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
                    .set("allPassed", status.isPassed() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
                    .set("evaluator", status.getLastEdit() != null ? status.getLastEvaluator() : "None")
                    .set("evaluationDate", status.getFirstEvaluation() != null ? status.getFirstEvaluation().toDateTimeString() + " ("+status.getFirstEvaluation().diffForHumans(false)+")" : "None")
                    .set("lastEvaluation", (status.getLastEdit() != null ? status.getLastEdit().toDateTimeString() + " ("+status.getFirstEvaluation().diffForHumans(false)+")" : "None"))
                    .setColor(status.isPassed() ? MessageType.SUCCESS.getColor() : MessageType.ERROR.getColor())
                    .queue();
                return true;
            }

            switch (args[1]) {
                case "passed": {
                    if (args.length == 2) {
                        context.makeError("Do you want to pass the user in the quiz, combat or consensus?\n" +
                            "**Quiz**: ``!evals " + args[0] + " passed quiz``\n" +
                            "**Combat**: ``!evals " + args[0] + " passed combat``").queue();
                        return false;
                    }
                    if (args.length == 3) {
                        if (args[2].equalsIgnoreCase("quiz") || args[2].equalsIgnoreCase("combat") || args[2].equalsIgnoreCase("consensus")) {
                            if (collection.size() < 1) {
                                Long roblox_id = getRobloxId(args[0]);
                                avaire.getDatabase()
                                    .newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME)
                                    .where("roblox_id", roblox_id)
                                    .insert(statement -> {
                                        statement
                                            .set("roblox_username", getRobloxUsernameFromId(roblox_id))
                                            .set("roblox_id", roblox_id).set("evaluator",
                                                context.getMember().getEffectiveName());
                                        if (args[2].equalsIgnoreCase("quiz")) {
                                            statement.set("passed_quiz", true);
                                        } else if (args[2].equalsIgnoreCase("combat")) {
                                            statement.set("passed_combat", true);
                                        } else if (args[2].equalsIgnoreCase("consensus")) {
                                            statement.set("passed_consensus", true);
                                        }
                                    });
                                context.makeSuccess("Successfully added the record to the database").queue();
                                avaire.getShardManager().getTextChannelById("980947919022731315").sendMessageEmbeds(context.makeSuccess("`" + args[0] + "` has passed the `" + args[2] + "` eval.").buildEmbed()).queue();

                                return true;
                            }
                            if (collection.size() > 2) {
                                context.makeError("Something is wrong in the database, there are records with " +
                                    "multiple usernames, but the same user id. Please check if this is correct.").queue();
                                return false;
                            }
                            if (collection.size() == 1) {
                                Long roblox_id = getRobloxId(args[0]);
                                avaire.getDatabase()
                                    .newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME)
                                    .where("roblox_id", roblox_id)
                                    .update(statement -> {
                                        if (args[2].equalsIgnoreCase("quiz")) {
                                            statement.set("passed_quiz", true);
                                        } else if (args[2].equalsIgnoreCase("combat")) {
                                            statement.set("passed_combat", true);
                                        } else if (args[2].equalsIgnoreCase("consensus")) {
                                            statement.set("passed_consensus", true);
                                        }
                                        statement.set("evaluator", context.getMember().getEffectiveName());
                                    });
                                context.makeSuccess("Successfully updated the record in the database").queue();
                                avaire.getShardManager().getTextChannelById("980947919022731315").sendMessageEmbeds(context.makeSuccess("`" + args[0] + "` has passed the `" + args[2] + "` eval.").requestedBy(context.getMember()).buildEmbed()).queue();
                                try {

                                    EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(roblox_id);
                                    if (status.isPassed()) {
                                        avaire.getShardManager().getTextChannelById("980947919022731315").sendMessageEmbeds(context.makeSuccess("`" + args[0] + "` has now passed all evaluations!").setColor(new Color(255, 215, 0)).requestedBy(context).buildEmbed()).queue();

                                        avaire.getRobloxAPIManager().getKronosManager().modifyEvalStatus(roblox_id, "pbst", true);

                                        return true;
                                    }
                                } catch (Exception e) {
                                    context.makeError("Something went wrong: ```" + e.getMessage() + "```").queue();
                                    return false;
                                }
                            }

                            return true;
                        } else {
                            context.makeError("Do you want to pass the user in the quiz, combat or consensus?\n" +
                                "**Quiz**: ``!evals " + args[0] + " passed quiz``\n" +
                                "**Combat**: ``!evals " + args[0] + " passed combat``").queue();
                            return false;
                        }
                    }
                }
                case "failed": {
                    if (args.length == 2) {
                        context.makeError("Do you want to fail the user in the quiz, combat or consensus?\n" +
                            "**Quiz**: ``!evals " + args[0] + " failed quiz``\n" +
                            "**Combat**: ``!evals " + args[0] + " failed combat``").queue();
                        return false;
                    }
                    if (args.length == 3) {
                        if (args[2].equalsIgnoreCase("quiz") || args[2].equalsIgnoreCase("combat") || args[2].equalsIgnoreCase("consensus")) {
                            if (collection.size() < 1) {
                                Long roblox_id = getRobloxId(args[0]);
                                avaire.getDatabase()
                                    .newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME)
                                    .where("roblox_id", roblox_id)
                                    .insert(statement -> {
                                        statement
                                            .set("roblox_username", getRobloxUsernameFromId(roblox_id))
                                            .set("roblox_id", roblox_id).set("evaluator",
                                                context.getMember().getEffectiveName());
                                        if (args[2].equalsIgnoreCase("quiz")) {
                                            statement.set("passed_quiz", false);
                                            avaire.getRobloxAPIManager().getEvaluationManager().getCooldownCache()
                                                .put("evaluation." + roblox_id + ".cooldown", true, 60 * 60 * 48);
                                        } else if (args[2].equalsIgnoreCase("combat")) {
                                            statement.set("passed_combat", false);
                                        } else if (args[2].equalsIgnoreCase("consensus")) {
                                            statement.set("passed_consensus", false);
                                        }
                                        statement.set("evaluator", context.getMember().getEffectiveName());
                                    });
                                context.makeSuccess("Successfully added the record to the database").queue();
                                return true;
                            }
                            if (collection.size() > 2) {
                                context.makeError("Something is wrong in the database, there are records with " +
                                    "multiple usernames, but the same user id. Please check if this is correct.").queue();
                                return false;
                            }
                            if (collection.size() == 1) {
                                Long roblox_id = getRobloxId(args[0]);
                                avaire.getDatabase()
                                    .newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME)
                                    .where("roblox_id", roblox_id)
                                    .update(statement -> {
                                        if (args[2].equalsIgnoreCase("quiz")) {
                                            statement.set("passed_quiz", false);
                                            avaire.getRobloxAPIManager().getEvaluationManager().getCooldownCache()
                                                .put("evaluation." + roblox_id + ".cooldown", true, 60 * 60 * 48);
                                        } else if (args[2].equalsIgnoreCase("combat")) {
                                            statement.set("passed_combat", false);
                                        } else if (args[2].equalsIgnoreCase("consensus")) {
                                            statement.set("passed_consensus", false);
                                        }
                                    });
                                context.makeSuccess("Successfully updated the record in the database").queue();

                                EvaluationStatus status = avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(roblox_id);
                                if (status.isPassed()) {
                                    avaire.getRobloxAPIManager().getKronosManager().modifyEvalStatus(roblox_id, "pbst", false);
                                    return false;
                                }
                                return true;
                            }


                        } else {
                            context.makeError("Do you want to pass the user in the quiz, combat or consensus?" +
                                "**Quiz**: ``!evals " + args[0] + " passed quiz``\n" +
                                "**Combat**: ``!evals " + args[0] + " passed combat``").queue();
                            return false;
                        }
                    }
                }
                default:
                    context.makeError("Do you want to pass the user in the quiz, combat or in the consensus?\n" +
                        "**Consensus**: ``!evals " + args[0] + " passed consensus``\n" +
                        "**Quiz**: ``!evals " + args[0] + " passed quiz``\n" +
                        "**Combat**: ``!evals " + args[0] + " passed combat``" +
                        "\nDo you want to fail the user in the quiz or in the consensus?\n" +
                        "**Consensus**: ``!evals " + args[0] + " failed consensus``\n" +
                        "**Quiz**: ``!evals " + args[0] + " failed quiz``\n" +
                        "**Combat**: ``!evals " + args[0] + " failed combat``").queue();
                    return false;
            }


        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
            return false;
        }

    }

    private boolean returnEvaluatorAction(CommandMessage context, String[] args) {
        if (args.length < 2) {
            context.makeError("You didn't specify a user:\n``!evals evaluator <@user>``").queue();
            return false;
        }

        if (!(context.getMessage().getMentions().getMembers().size() > 0)) {
            context.makeError("Please mention members in this guild.").queue();
            return false;
        }
        List <Member> members = context.getMessage().getMentions().getMembers();
        Role r = context.guild.getRolesByName("Evaluators", true).get(0);

        for (Member m : members) {
            if (m.getRoles().contains(r)) {
                context.guild.removeRoleFromMember(m, r).queue(p -> {
                    GuildTransformer transformer = context.getGuildTransformer();

                    if (transformer == null) {
                        context.makeError("The guild informormation coudn't be pulled. Please check with Stefano#7366" +
                            ".").queue();
                        return;
                    }

                    if (transformer.getModlog() != null) {
                        context.getGuild().getTextChannelById(transformer.getModlog())
                            .sendMessageEmbeds(context.makeEmbeddedMessage()
                                .setTitle("\uD83D\uDCDC Evaluator Role Removed")
                                .addField("User", m.getEffectiveName(), true).addField("Command Executor",
                                    context.member.getEffectiveName(), true)
                                .buildEmbed()).queue();
                    }
                });
                context.makeSuccess("Successfully removed the ``Evaluator`` role from " + m.getAsMention()).queue();

            } else {
                context.guild.addRoleToMember(m, r).queue(p -> {
                    GuildTransformer transformer = context.getGuildTransformer();
                    if (transformer == null) {
                        context.makeError("The guild informormation coudn't be pulled. Please check with Stefano#7366" +
                            ".").queue();
                        return;
                    }

                    if (transformer.getModlog() != null) {
                        context.getGuild().getTextChannelById(transformer.getModlog())
                            .sendMessageEmbeds(context.makeEmbeddedMessage()
                                .setTitle("\uD83D\uDCDC Evaluator Role Added")
                                .addField("User", m.getEffectiveName(), true).addField("Command Executor",
                                    context.member.getEffectiveName(), true)
                                .buildEmbed()).queue();
                    }
                });
                context.makeSuccess("Successfully added the ``Evaluator`` role to " + m.getAsMention()).queue();
            }
        }


        return true;
    }

    private static String getRobloxUsernameFromId(Long id) {
        try {
            JSONObject json = readJsonFromUrl("https://api.roblox.com/users/" + id);
            return json.getString("Username");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return 0L;
        }
    }

    public boolean isValidRobloxUser(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un) != 0;
        } catch (Exception e) {
            return false;
        }
    }
}
