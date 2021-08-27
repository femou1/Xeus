package com.pinewoodbuilders.servlet.routes.v1.get;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.servlet.routes.v1.post.PostAccountVerificationLink;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;

public class GetAccountVerificationLink extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(PostAccountVerificationLink.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidVerificationAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid `Authorization` header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid `Authorization` header give.");
        }
        String robloxId = request.params("robloxId");
        JSONObject root = new JSONObject();

        HashMap<Long, String> inVerificationSystem = Xeus.getInstance().getRobloxAPIManager().getVerification().getInVerification();
        if (inVerificationSystem.containsKey(Long.valueOf(robloxId))) {
            String verification = inVerificationSystem.get(Long.valueOf(robloxId));
            String[] verificationData = verification.split(":");

            String userId = verificationData[0];
            String verificationCode = verificationData[1];

            User u = Xeus.getInstance().getShardManager().getUserById(userId);

            if (u != null) {
                root.put("username", u.getName() + "#" + u.getDiscriminator());
                root.put("inVerification", true);
                root.put("verificationId", verificationCode);
            } else {
                response.status(401);
                root.put("inVerification", false);
                root.put("error", String.format("Invalid verification code for %s (%s), access denied.", userId, robloxId));
            }
        } else {
            response.status(200);
            root.put("inVerification", false);
            root.put("error", String.format("Account (%s) is not running any verification.", robloxId));
        }

        return root;
    }
}
