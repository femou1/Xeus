package com.pinewoodbuilders.servlet.routes.v1.post;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class PostAcknowledgedMessage extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(PostEvalAnswers.class);

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

        return root;
    }

}
