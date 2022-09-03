package com.pinewoodbuilders.servlet.handlers.oauth;

import com.github.scribejava.apis.RobloxApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.oauth.OAuthRequest;
import com.pinewoodbuilders.utilities.RandomUtil;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class OAUTH2Manager {
    private final Xeus xeus;
    private final OAuth20Service oauthService;
    private final ExpiringMap <String, OAuthRequest> requests;

    public OAUTH2Manager(Xeus xeus, String clientId, String clientSecret, String callbackUrl) {
        this.xeus = xeus;
        this.oauthService = new ServiceBuilder(clientId)
            .apiSecret(clientSecret)
            .callback(callbackUrl)
            .build(RobloxApi.instance());

        this.requests = ExpiringMap.builder()
            .maxSize(100)
            .expirationPolicy(ExpirationPolicy.CREATED)
            .expiration(5, TimeUnit.MINUTES)
            .build();

    }

    public String makeVerificationAuthRequest(Long robloxId, Long discordId, String state) {
        if (state == null) state = RandomUtil.generateString(10);
        requests.put(state, new OAuthRequest(discordId, robloxId, state));
        return oauthService.createAuthorizationUrlBuilder().state(state).build();
    }

    public JSONObject handleVerificationRedirect(Request request, Response response) throws IOException, ExecutionException, InterruptedException {
        OAuth2AccessToken accessToken = oauthService.getAccessToken(request.params("code"));




        xeus.getRobloxAPIManager().getVerification().getInVerification()

        return null;
    }
}
