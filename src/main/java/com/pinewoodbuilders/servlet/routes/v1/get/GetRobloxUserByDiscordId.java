package com.pinewoodbuilders.servlet.routes.v1.get;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class GetRobloxUserByDiscordId extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger("V(D>R)");

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidVerificationAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid \"Authorization\" header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid \"Authorization\" header give.");
        }
        String id = request.params("discordId");

        JSONObject root = new JSONObject();
        VerificationEntity verificationEntity = Xeus.getInstance().getRobloxAPIManager().getVerification().fetchVerification(id, true, "pinewood");

        if (verificationEntity == null) {
            verificationEntity = Xeus.getInstance().getRobloxAPIManager().getVerification().fetchVerification(id, true, "rover");
        }

        if (verificationEntity == null) {
            verificationEntity = Xeus.getInstance().getRobloxAPIManager().getVerification().fetchVerification(id, true, "bloxlink");
        }

        if (verificationEntity != null) {
            root.put("username", verificationEntity.getRobloxUsername());
            root.put("robloxId", verificationEntity.getRobloxId());
            root.put("provider", verificationEntity.getProvider());
            log.info("{} ({}) -> {} ({})", verificationEntity.getDiscordId(), returnUsernameFromDiscord(verificationEntity.getDiscordId()), verificationEntity.getRobloxId(), verificationEntity.getRobloxUsername());
            response.status(200);
        } else {
            root.put("error", true);
            root.put("message", "The ID " + id + " doesn't have a user in any verification database.");
            log.info("{} -> None", id);
            response.status(404);
        }

        return root;
    }

    private String returnUsernameFromDiscord(Long discordId) {
        User u = Xeus.getInstance().getShardManager().getUserById(discordId);
        if (u != null) {
            return u.getName() + "#" + u.getDiscriminator();
        }
        return "Unknown";
    }
}
