package com.avairebot.servlet.routes.v1.get;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.contracts.metrics.SparkRoute;
import com.avairebot.contracts.verification.VerificationEntity;
import com.avairebot.database.collection.Collection;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.SQLException;

public class GetDiscordIdsByRobloxId extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(GetDiscordIdsByRobloxId.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidVerificationAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid \"Authorization\" header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid \"Authorization\" header give.");
        }

        String id = request.params("robloxId");

        JSONObject root = new JSONObject();
        VerificationEntity verificationEntity = callUserFromDatabaseAPI(id);

        if (verificationEntity != null) {
            root.put("username", verificationEntity.getRobloxUsername());
            root.put("robloxId", verificationEntity.getRobloxId());
            root.put("provider", verificationEntity.getProvider());
            root.put("discordId", verificationEntity.getDiscordId());
            root.put("discordUsername", returnUsernameFromDiscord(verificationEntity.getDiscordId()));
            response.status(200);
        } else {
            root.put("error", true);
            root.put("message", "The ID " + id + " doesn't have a user in any verification database.");
            response.status(404);
        }

        return root;
    }

    private String returnUsernameFromDiscord(Long discordId) {
        User u = AvaIre.getInstance().getShardManager().getUserById(discordId);
        if (u != null) {
            return u.getName() + "#" + u.getDiscriminator();
        }
        return "Unkown";
    }


    public VerificationEntity callUserFromDatabaseAPI(String robloxId) {
        try {
            Collection linkedAccounts = AvaIre.getInstance().getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME).where("robloxId", robloxId).get();
            if (linkedAccounts.size() == 0) {
                return null;
            } else {
                return new VerificationEntity(linkedAccounts.first().getLong("robloxId"), AvaIre.getInstance().getRobloxAPIManager().getUserAPI().getUsername(linkedAccounts.first().getLong("robloxId")), linkedAccounts.first().getLong("id"), "pinewood");
            }
        } catch (SQLException throwables) {
            return null;
        }
    }
}
