package com.avairebot.servlet.routes.v1.get;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.contracts.metrics.SparkRoute;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.collection.DataRow;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class GetEvaluationStatus extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(GetEvaluationStatus.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidEvaluationsAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid `Authorization` header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid `Authorization` header give.");
        }
        String robloxId = request.params("robloxId");
        JSONObject root = new JSONObject();

        if (AvaIre.getInstance().getRobloxAPIManager().getUserAPI().getUsername(Long.valueOf(robloxId)) == null) {
            root.put("error", 404);
            root.put("message", "Sorry, but the ID of this user doesn't exist. Please try again later.");
            return root;
        }

        Collection collection = AvaIre.getInstance().getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", robloxId).get();
        if (collection.size() < 1) {
            root.put("passed_quiz", false);
            root.put("passed_combat", false);
            root.put("passed_patrol", false);

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
        root.put("quizPending", AvaIre.getInstance().getRobloxAPIManager().getEvaluationManager().hasPendingQuiz(Long.valueOf(robloxId)));
        return root;
    }
}
