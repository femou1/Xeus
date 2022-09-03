package com.pinewoodbuilders.servlet.routes.v1.get;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import spark.Request;
import spark.Response;

public class GetOauth2Redirect extends SparkRoute {
    /**
     * Invoked when a request is made on this route's corresponding path e.g. '/hello'
     *
     * @param request  The request object providing information about the HTTP request
     * @param response The response object providing functionality for modifying the response
     * @return The content to be set in the response
     * @throws Exception implementation can choose to throw exception
     */
    @Override
    public Object handle(Request request, Response response) throws Exception {
        return Xeus.getInstance().getOauth2Manager().handleVerificationRedirect(request, response);
    }
}
