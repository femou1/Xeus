package com.pinewoodbuilders.servlet.routes.v1.get;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.cache.CacheAdapter;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.List;

public class GetEvaluationStatus extends SparkRoute {

    private final Logger log = LoggerFactory.getLogger(GetEvaluationStatus.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidEvaluationsAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid `Authorization` header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid `Authorization` header give.");
        }

        Long guildId = Long.valueOf(request.params("guildId"));
        Long robloxId = Long.valueOf(request.params("robloxId"));
        JSONObject root = new JSONObject();

        if (Xeus.getInstance().getRobloxAPIManager().getUserAPI().getUsername(robloxId) == null) {
            response.status(404);
            root.put("error", 404);
            root.put("message", "Sorry, but the ID of this user doesn't exist. Please try again later.");
            return root;
        }

        Long points = checkPoints(robloxId);

        Collection collection = Xeus.getInstance().getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", robloxId).get();
        if (collection.size() < 1) {
            root.put("passed_quiz", false);
            root.put("quizPending", Xeus.getInstance().getRobloxAPIManager().getEvaluationManager().hasPendingQuiz(robloxId));
            root.put("enoughPoints", points >= 75);
            root.put("points", points);
            root.put("rankLocked", Xeus.getInstance().getRobloxAPIManager().getKronosManager().isRanklocked(robloxId));
            root.put("onCooldown", getCooldownFromCache(robloxId));
            root.put("isEvalRank", isEvalRank(guildId, robloxId));
            root.put("roblox_id", robloxId);
            return root;
        }

        if (collection.size() > 2) {
            response.status(500);
            root.put("error", 500);
            root.put("message", "There has been a mistake in the database, please contact the Xeus administrator to solve this issue.");
            return root;
        }

        DataRow row = collection.get(0);
        Boolean pq = row.getBoolean("passed_quiz");
        Boolean pp = row.getBoolean("passed_patrol");
        Boolean pc = row.getBoolean("passed_combat");


        String evaluator = row.getString("evaluator") != null ? row.getString("evaluator") : "Unkown Evaluator";

        root.put("passed_quiz", pq);
        root.put("passed_patrol", pp);
        root.put("passed_combat", pc);
        root.put("evaluator", evaluator);
        root.put("roblox_id", robloxId);
        root.put("quizPending", Xeus.getInstance().getRobloxAPIManager().getEvaluationManager().hasPendingQuiz(robloxId));
        root.put("enoughPoints", points >= 75);
        root.put("rankLocked", Xeus.getInstance().getRobloxAPIManager().getKronosManager().isRanklocked(robloxId));
        root.put("onCooldown", getCooldownFromCache(robloxId));
        root.put("isEvalRank", isEvalRank(guildId, robloxId));
        root.put("message", row.getString("message"));
        return root;
    }

    private long checkPoints(Long robloxId) {
        return Xeus.getInstance().getRobloxAPIManager().getKronosManager().getPoints(robloxId);
    }

    private boolean isEvalRank(Long id, Long robloxId) {
        Guild guild = Xeus.getInstance().getShardManager().getGuildById(id);
        if (guild != null) {

            GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(Xeus.getInstance(), guild);
            if (transformer.getRobloxGroupId() != 0) {
                List <RobloxUserGroupRankService.Data> ranks = Xeus.getInstance().getRobloxAPIManager()
                    .getUserAPI().getUserRanks(robloxId).stream()
                    .filter(groupRanks -> groupRanks.getGroup().getId() == transformer.getRobloxGroupId()).toList();
                if (ranks.size() == 0) return false;
                if (transformer.getRobloxGroupId() != 645836) return false;

                return ranks.get(0).getRole().getRank() == 2;
            }

        }
        return true;
    }

    private boolean getCooldownFromCache(Long robloxId) {
        CacheAdapter cache = Xeus.getInstance().getRobloxAPIManager().getEvaluationManager().getCooldownCache();
        return cache.get("evaluation." + robloxId + ".cooldown") != null;
    }
}
