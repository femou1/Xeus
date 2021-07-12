package com.avairebot.servlet.routes.v1;

import com.avairebot.contracts.metrics.SparkRoute;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class PostAccountVerificationLink extends SparkRoute {
    private static final Logger log = LoggerFactory.getLogger(PostAccountVerificationLink.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (!hasValidVerificationAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid `Authorization` header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid `Authorization` header give.");
        }

        JSONObject obj = new JSONObject(request.body());
        //obj.getString()

        return buildResponse(response, 200, String.format("Confirmation, ", 1));
    }
}
