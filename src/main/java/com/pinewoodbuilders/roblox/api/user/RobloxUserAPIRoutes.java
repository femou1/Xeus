package com.pinewoodbuilders.roblox.api.user;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.requests.service.user.inventory.RobloxGamePassService;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RobloxUserAPIRoutes {

    private final Xeus avaire;
    private final RobloxAPIManager manager;
    private final Request.Builder request = new Request.Builder()
        .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version);

    public RobloxUserAPIRoutes(Xeus avaire, RobloxAPIManager robloxAPIManager) {this.avaire = avaire; this.manager = robloxAPIManager;}

    public static final Cache <String, String> cache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

    public List<RobloxUserGroupRankService.Data> getUserRanks(Long botAccount) {
        request.url("https://groups.roblox.com/v2/users/{userId}/groups/roles".replace("{userId}", botAccount.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                RobloxUserGroupRankService grs = (RobloxUserGroupRankService) manager.toService(response, RobloxUserGroupRankService.class);
                if (!grs.hasData()) {
                    return null;
                }
                return grs.getData();
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return null;
    }

    public String getUserStatus(Long botAccount) {
        request.url("https://users.roblox.com/v1/users/{userId}".replace("{userId}", botAccount.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                JSONObject json = new JSONObject(response.body().string());
                return json.getString("description");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return null;
    }

    public String getUsername(String userId) {
        String username = cache.getIfPresent("username." + userId);
        if (username != null) {
            return username;
        }

        request.url("https://users.roblox.com/v1/users/{userId}".replace("{userId}", userId));
        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                JSONObject json = new JSONObject(response.body().string());
                cache.put("username." + userId, json.getString("name"));
                return json.getString("name");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return null;
    }

    public long getIdFromUsername(String username) {
        String userId = cache.getIfPresent("robloxId." + username);
        if (userId != null) {
            return Long.parseLong(userId);
        }

        request.url("https://users.roblox.com/v1/users/{userId}".replace("{userId}", username));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                JSONObject json = new JSONObject(response.body().string());
                cache.put("robloxId." + username, json.getString("id"));
                return json.getLong("id");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return 0;
    }


    public List<RobloxGamePassService.Datum> getUserGamePass(Long userId, Long gamepassId) {
        request.url("https://inventory.roblox.com/v1/users/{userId}/items/GamePass/{gamepassId}"
                        .replace("{userId}", userId.toString())
                        .replace("{gamepassId}", gamepassId.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                RobloxGamePassService grs = (RobloxGamePassService) manager.toService(response, RobloxGamePassService.class);
                if (grs.hasData()) {
                    return grs.getData();
                }
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }
        return null;
    }

    public String getUsername(Long userId) {
        return getUsername(String.valueOf(userId));
    }

}
