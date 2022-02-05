package com.pinewoodbuilders.servlet.routes.v1.post;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.contracts.roblox.evaluations.EvaluationStatus;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

public class PostEvalAnswers extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(PostEvalAnswers.class);
    private final RobloxAPIManager manager = Xeus.getInstance().getRobloxAPIManager();

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidEvaluationsAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid `Authorization` header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid `Authorization` header give.");
        }
        String guildId = request.params("guildId");
        JSONObject root = new JSONObject();
        Guild guild = Xeus.getInstance().getShardManager().getGuildById(guildId);
        if (guild == null) {
            response.status(400);
            root.put("error", "XEUS_GUILD_DOES_NOT_EXIST");
            root.put("message", "Guild doesn't exist. :(");
            return root;
        }

        GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(Xeus.getInstance(), guild);

        if (transformer == null) {
            response.status(500);
            root.put("error", "XEUS_GUILD_MISSING_TRANSFORMER");
            root.put("message", "Guild doesn't have a transformer, please check with the guild admins and api developer (Stefano#7366).");
            return true;
        }

        JSONObject post = new JSONObject(request.body());
        if (!post.has("userId")) {
            response.status(400);
            root.put("error", "XEUS_MISSING_ROBLOX_ID");
            root.put("message", "I'm missing a Roblox UserID. :(");
            return root;
        }

        Long userId = post.getLong("userId");
        String username = manager.getUserAPI().getUsername(userId);
        if (username == null) {
            response.status(400);
            root.put("error", "XEUS_USER_ID_NOT_EXISTANT");
            root.put("message", "The userId you gave me doesn't exist.");
            return root;
        }

        if (manager.getEvaluationManager().hasPendingQuiz(userId)) {
            response.status(401);
            root.put("error", "XEUS_QUIZ_IS_PENDING");
            root.put("message", "This user already has a quiz pending.");
            return root;
        }

        EvaluationStatus evals = manager.getEvaluationManager().getEvaluationStatus(userId);
        if (evals == null) {
            response.status(500);
            root.put("error", "XEUS_NO_EVAL_LIST");
            root.put("message", "Something went wrong on the database.");
            return root;
        }

        if (evals.passedQuiz()) {
            response.status(401);
            root.put("error", "XEUS_ALREADY_PASSED_QUIZ");
            root.put("message", "This user has already passed the quiz. Questions cannot be submitted for this user.");
            return root;
        }

        if (!post.has("questions")) {
            response.status(400);
            root.put("error", "XEUS_MISSING_QUESTIONS");
            root.put("message", "I'm missing the questions. :(");
            return root;
        }

        try {
            if (post.getJSONArray("questions") == null) {
                response.status(500);
                root.put("error", "XEUS_QUESTIONS_ARE_NULL");
                root.put("message", "Hmm? This is a wierd error. How did this happen? :(");
                return root;
            }
        } catch (JSONException jsonEx) {
            response.status(400);
            root.put("error", "XEUS_QUESTIONS_ARE_NOT_AN_ARRAY");
            root.put("message", "The questions have to be in an array format. :(");
            return root;
        }

        JSONArray questionsWithAnswers = post.getJSONArray("questions");
        if (questionsWithAnswers.length() > 10) {
            response.status(400);
            root.put("error", "XEUS_LESS_OR_MORE_THEN_EIGHT_RESPONSES");
            root.put("message", "I need less then 10 questions, sorry. This endpoint won't expect more. :(");
            return root;
        }



        List <MessageEmbed> messageList = new LinkedList <>();
        for (Object questionsWithAnswer : questionsWithAnswers) {
            JSONObject jsonObj = (JSONObject) questionsWithAnswer;
            if (!(jsonObj.has("question") && jsonObj.has("answer"))) {
                response.status(400);
                root.put("error", "XEUS_MISSING_QUESTION_OR_ANSWER");
                root.put("message", "One of the objects is either missing an question or an answer.");
                return root;
            }

            if (transformer.getEvaluationEvalChannel() == 0 || guild.getTextChannelById(transformer.getEvaluationEvalChannel()) == null) {
                response.status(500);
                root.put("error", "XEUS_MISSING_GUILD_EVAL_CHANNEL");
                root.put("message", "Eval answer channel has not been set, or does not exist. Please contact the guild admins.");
                return root;
            }

            String question = jsonObj.getString("question");
            String answer = jsonObj.getString("answer").length() > 0 ? jsonObj.getString("answer") : "Question was not answered.";
            messageList.add(buildQuestionAndAnswerEmbed(question, answer, messageList, username, post.has("points") ? post.getLong("points") : null, questionsWithAnswers.length()));
        }

 
        TextChannel tc = guild.getTextChannelById(transformer.getEvaluationEvalChannel());
        if (tc != null) {
            tc.sendMessageEmbeds(messageList).setActionRow(Button.success("approve", Emoji.fromUnicode("\uD83D\uDC4D")), Button.danger("reject", Emoji.fromUnicode("⛔"))).queue(
                message -> manager.getEvaluationManager().addQuizToDatabase(userId, guild.getIdLong(), message.getIdLong()));
        }
        root.put("success", true);

        return root;
    }

    private MessageEmbed buildQuestionAndAnswerEmbed(String question, String answer, List <MessageEmbed> messageList, String username, Long points, int questionAmount) {
        EmbedBuilder eb = new EmbedBuilder();
        if (messageList.size() == 0) {
            eb.setTitle(username);
        }

        eb.setDescription("**" + question + "**\n```" + answer + "```");

        if (messageList.size() == questionAmount) {
            eb.setFooter(username + (points != null ? " - " + points : ""), "https://xeus.pinewood-builders.com/img/xeus-1024x1024.png")
                .setTimestamp(Instant.now());
        }
        return eb.build();
    }
}
