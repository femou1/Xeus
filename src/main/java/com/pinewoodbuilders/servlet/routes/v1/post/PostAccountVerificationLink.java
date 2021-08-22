package com.pinewoodbuilders.servlet.routes.v1.post;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;

public class PostAccountVerificationLink extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(PostAccountVerificationLink.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidVerificationAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid `Authorization` header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid `Authorization` header give.");
        }
        JSONObject post = new JSONObject(request.body());
        JSONObject root = new JSONObject();

        String robloxId = request.params("robloxId");

        HashMap<Long, String> inVerificationSystem = Xeus.getInstance().getRobloxAPIManager().getVerification().getInVerification();
        if (inVerificationSystem.containsKey(Long.valueOf(robloxId))) {
            String verification = inVerificationSystem.get(Long.valueOf(robloxId));
            String[] verificationData = verification.split(":");

            String discordId = verificationData[0];
            String verificationCode = verificationData[1];

            if (post.has("verificationId")) {
                if (verificationCode.equals(post.getString("verificationId"))) {
                    root.put("verified", true);
                    root.put("verificationId", verificationCode);
                    inVerificationSystem.replace(Long.valueOf(robloxId), discordId + ":verified");
                } else {
                    response.status(401);
                    root.put("verified", false);
                    root.put("verificationId", post.getString("verificationId"));
                    root.put("error", "Invalid verification code!");
                }
            } else {
                response.status(401);
                root.put("verified", false);
                root.put("error", String.format("Missing. (%s)", robloxId));
            }

        } else {
            response.status(404);
            root.put("inVerification", false);
            root.put("error", String.format("Account (%s) is not running any verification.", robloxId));

        }

        return root;
    }
}
