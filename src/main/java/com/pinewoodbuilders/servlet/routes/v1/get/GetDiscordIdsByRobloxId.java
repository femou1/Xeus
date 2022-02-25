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

public class GetDiscordIdsByRobloxId extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger("Verification (R->D)");

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidVerificationAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid \"Authorization\" header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid \"Authorization\" header give.");
        }

        String id = request.params("robloxId");

        JSONObject root = new JSONObject();
        VerificationEntity verificationEntity = Xeus.getInstance().getRobloxAPIManager().getVerification().callDiscordUserFromDatabaseAPI(id);

        if (verificationEntity != null) {
            root.put("username", verificationEntity.getRobloxUsername());
            root.put("robloxId", verificationEntity.getRobloxId());
            root.put("provider", verificationEntity.getProvider());
            root.put("discordId", verificationEntity.getDiscordId());
            root.put("discordUsername", returnUsernameFromDiscord(verificationEntity.getDiscordId()));
            log.info("{} ({}) -> {} ({})", verificationEntity.getRobloxId(), verificationEntity.getRobloxUsername(), verificationEntity.getDiscordId(), returnUsernameFromDiscord(verificationEntity.getDiscordId()));
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
        return "ERROR: Unable to get user...";
    }


}
