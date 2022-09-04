package com.pinewoodbuilders.servlet.handlers.oauth;

import com.github.scribejava.apis.RobloxApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.oauth.XeusOAuthRequest;
import com.pinewoodbuilders.utilities.RandomUtil;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class OAUTH2Manager {
    private final Xeus xeus;
    private final OAuth20Service oauthService;
    private final ExpiringMap <String, XeusOAuthRequest> requests;

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
        requests.put(state, new XeusOAuthRequest(discordId, robloxId, state));
        return oauthService.createAuthorizationUrlBuilder().scope("openid profile").state(state).build();
    }

    public JSONObject handleVerificationRedirect(Request request, Response response) throws IOException, ExecutionException, InterruptedException {
        if (!requests.containsKey(request.queryParams("state"))) {
            response.status(400);
            return new JSONObject().put("error", true).put("message", "Incorrect state.");
        }
        XeusOAuthRequest xeusOAuthRequest = requests.get(request.queryParams("state"));
        if (xeusOAuthRequest == null) {
            response.status(400);
            return new JSONObject().put("error", true).put("message", "Unable to retrieve oauth request.");
        }

        OAuth2AccessToken accessToken = oauthService.getAccessToken(request.queryParams("code"));
        final OAuthRequest authRequest = new OAuthRequest(Verb.GET, "https://apis.roblox.com/oauth/v1/userinfo");
        oauthService.signRequest(accessToken, authRequest);

        JSONObject profileInfo;
        try (com.github.scribejava.core.model.Response execute = oauthService.execute(authRequest)) {
            if (!execute.isSuccessful()) {
                response.status(400);
                return new JSONObject().put("error", true).put("message", "Unable to obtain profile info.");
            }

            profileInfo = new JSONObject(execute.getBody());
        }

        if (profileInfo.getLong("sub") != xeusOAuthRequest.robloxId()) {
            response.status(400);
            return new JSONObject().put("error", true).put("message", "Profile does not match the account entered during the verification request.");
        }

        HashMap <Long, String> inVerificationSystem = xeus.getRobloxAPIManager().getVerification().getInVerification();

        String verification = inVerificationSystem.get(profileInfo.getLong("sub"));
        if (verification == null) {
            response.status(400);
            return new JSONObject().put("error", true).put("message", "Profile is currently not requesting verification.");
        }

        String[] verificationData = verification.split(":");

        String discordId = verificationData[0];
        inVerificationSystem.replace(profileInfo.getLong("sub"), discordId + ":verified");

        return new JSONObject().put("error", false).put("message", "User successfully verified.");
    }
}
