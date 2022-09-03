package com.pinewoodbuilders.servlet.handlers.oauth;

import com.github.scribejava.apis.RobloxApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.pinewoodbuilders.Xeus;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

public class OAUTH2Manager {
    private final Xeus xeus;
    private final OAuth20Service oauthService;

    public OAUTH2Manager(Xeus xeus, String clientId, String clientSecret, String callbackUrl) {
        this.xeus = xeus;
        this.oauthService = new ServiceBuilder(clientId)
            .apiSecret(clientSecret)
            .callback(callbackUrl)
            .build(RobloxApi.instance());
    }

    public void startAuthorization() {
        String authorizationUrl = oauthService.getAuthorizationUrl();

    }

    public JSONObject handleRedirect(Request request, Response response) {
        return null;
    }
}
