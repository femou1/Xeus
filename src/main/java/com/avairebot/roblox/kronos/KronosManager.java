package com.avairebot.roblox.kronos;

import com.avairebot.AppInfo;
import com.avairebot.AvaIre;
import com.avairebot.roblox.RobloxAPIManager;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class KronosManager {

    private final AvaIre avaire;
    private final RobloxAPIManager manager;
    protected final String apikey;

    public KronosManager(AvaIre avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
        this.apikey = avaire.getConfig().getString("apiKeys.kronosDatabaseApiKey");
    }

    public Long getPoints(Long userId) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", apikey)
            .url("https://www.pb-kronos.dev/api/v2/database/pbst?userids=" + userId);

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();
                JSONArray array = new JSONArray(body);

                if (array.length() == 0) {
                    return 0L;
                }

                JSONObject jsonObject = (JSONObject) array.get(0);
                return jsonObject.getLong("Points");
            } else if (response.code() == 404) {
                return 0L;
            } else {
                throw new Exception("Kronos API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            AvaIre.getLogger().error("Failed sending request to Kronos API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    public boolean isRanklocked(Long userId) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", apikey)
            .url("https://www.pb-kronos.dev/api/v2/database/pbst?userids=" + userId);

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();
                JSONArray array = new JSONArray(body);

                if (array.length() == 0) {
                    return false;
                }

                JSONObject jsonObject = (JSONObject) array.get(0);
                if (!jsonObject.has("ExtraData")) {
                    return false;
                }
                if (!jsonObject.getJSONObject("ExtraData").has("Ranklock")) {
                    return false;
                }
                return jsonObject.getJSONObject("ExtraData").getInt("Ranklock") >= 1;
            } else if (response.code() == 404) {
                return false;
            } else {
                throw new Exception("Kronos API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            AvaIre.getLogger().error("Failed sending request to Kronos API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


}
