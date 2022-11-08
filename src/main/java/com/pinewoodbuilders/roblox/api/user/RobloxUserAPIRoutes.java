package com.pinewoodbuilders.roblox.api.user;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.requests.service.user.inventory.RobloxGamePassService;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
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

    public List <RobloxUserGroupRankService.Data> getUserRanks(Long botAccount) {
        request.get().url("https://groups.roblox.com/v2/users/{userId}/groups/roles".replace("{userId}", botAccount.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                RobloxUserGroupRankService grs = (RobloxUserGroupRankService) manager.toService(response, RobloxUserGroupRankService.class);
                if (!grs.hasData()) {
                    return null;
                }
                return grs.getData();
            } else {
                Xeus.getLogger().error("Failed sending request to Roblox Ranks API: Error code `" + response.code() + "`");
                return null;
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox Ranks API: " + e.getMessage());
        }
        return null;
    }

    public String getUserStatus(Long botAccount) {
        request.get().url("https://users.roblox.com/v1/users/{userId}".replace("{userId}", botAccount.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                JSONObject json = new JSONObject(response.body().string());
                return json.getString("description");
            } else {
                Xeus.getLogger().error("Failed sending request to Roblox User Desc API: Error code `" + response.code() + "`");
                return null;
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox User Desc API: " + e.getMessage());
        }
        return null;
    }

    public String getUsername(String userId) {
        String username = cache.getIfPresent("username." + userId);
        if (username != null) {
            return username;
        }

        request.get().url("https://users.roblox.com/v1/users/{userId}".replace("{userId}", userId));
        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                JSONObject json = new JSONObject(response.body().string());
                cache.put("username." + userId, json.getString("name"));
                return json.getString("name");
            } else {
                Xeus.getLogger().error("Failed sending request to Roblox Username API: Error code `" + response.code() + "`");
                return null;
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox Username API: " + e.getMessage());
        }
        return null;
    }

    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");

    public long getIdFromUsername(String username) {
        String userId = cache.getIfPresent("robloxId." + username);
        if (userId != null) {
            return Long.parseLong(userId);
        }


        request.url("https://users.roblox.com/v1/usernames/users")
            .post(RequestBody.create("{\"usernames\":[\"" + username + "\"], \"excludeBannedUsers\": false}", json));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                JSONObject json = new JSONObject(response.body().string());

                JSONArray array = json.getJSONArray("data");
                if (array.length() == 0) return 0;
                for (Object o : array) {
                    JSONObject user = (JSONObject) o;
                    if (user.getString("requestedUsername").equalsIgnoreCase(username)) {
                        cache.put("robloxId." + username, String.valueOf(user.getLong("id")));
                        return user.getLong("id");
                    }
                }
            } else {
                Xeus.getLogger().error("Failed sending request to Roblox Usernames API: Error code `" + response.code() + "`");
            }
            return 0;
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox Usernames API: " + e.getMessage());
        }
        return 0;
    }


    public List <RobloxGamePassService.Datum> getUserGamePass(Long userId, Long gamepassId) {
        request.get().url("https://inventory.roblox.com/v1/users/{userId}/items/GamePass/{gamepassId}"
            .replace("{userId}", userId.toString())
            .replace("{gamepassId}", gamepassId.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                RobloxGamePassService grs = (RobloxGamePassService) manager.toService(response, RobloxGamePassService.class);
                if (!grs.hasData()) {
                    return null;
                }
                return grs.getData();
            } else {
                Xeus.getLogger().error("Failed sending request to Roblox Gamepass API: Error code `" + response.code() + "`");
                return null;
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox Gamepass API: " + e.getMessage());
        }
        return null;
    }

    public boolean hasBadge(Long userId, Long gamepassId) {
        request.get().url("https://badges.roblox.com/v1/users/{userId}/badges/awarded-dates?badgeIds={gamepassId}"
            .replace("{userId}", userId.toString())
            .replace("{gamepassId}", gamepassId.toString()));

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray jArray = json.getJSONArray("data");
                return jArray.length() > 0;
            } else {
                Xeus.getLogger().error("Failed sending request to Roblox Gamepass API: Error code `" + response.code() + "`");
            }
            return false;
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox Gamepass API: " + e.getMessage());
        }
        return false;
    }


    public String getUsername(Long userId) {
        return getUsername(String.valueOf(userId));
    }

}
