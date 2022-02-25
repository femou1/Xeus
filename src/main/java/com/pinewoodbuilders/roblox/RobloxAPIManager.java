package com.pinewoodbuilders.roblox;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.roblox.api.group.GroupAPIRoutes;
import com.pinewoodbuilders.roblox.api.user.RobloxUserAPIRoutes;
import com.pinewoodbuilders.roblox.evaluations.EvaluationManager;
import com.pinewoodbuilders.roblox.kronos.KronosManager;
import com.pinewoodbuilders.roblox.verification.VerificationManager;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;

public class RobloxAPIManager {
    private final OkHttpClient client = new OkHttpClient();
    private final RobloxUserAPIRoutes userAPI;
    private final VerificationManager verification;
    private final GroupAPIRoutes groupAPI;
    private final EvaluationManager evaluationManager;
    private final KronosManager kronosManager;

    public RobloxAPIManager(Xeus avaire) {
        this.userAPI = new RobloxUserAPIRoutes(avaire, this);
        this.verification = new VerificationManager(avaire, this);
        this.groupAPI = new GroupAPIRoutes(avaire, this);
        this.evaluationManager = new EvaluationManager(avaire, this);
        this.kronosManager = new KronosManager(avaire, this);
    }

    public RobloxUserAPIRoutes getUserAPI() {
        return userAPI;
    }

    public VerificationManager getVerification() {
        return verification;
    }

    public GroupAPIRoutes getGroupAPI() {
        return groupAPI;
    }

    public EvaluationManager getEvaluationManager() {return evaluationManager;}

    public KronosManager getKronosManager() {
        return kronosManager;
    }

    public OkHttpClient getClient() {
        return client;
    }

    public Object toService(Response response, Class<?> clazz) {
        return Xeus.gson.fromJson(toString(response), clazz);
    }
    public Object toService(String response, Class<?> clazz) {
        return Xeus.gson.fromJson(response, clazz);
    }

    public String toString(Response response) {
        try {
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    return body.string();
                }
            }
        } catch (IOException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
        return null;
    }
}
