package com.pinewoodbuilders.servlet.routes.v1.get;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GetTextDevforumPost extends SparkRoute {
    public static final Cache <String, JSONObject> cache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterWrite(12, TimeUnit.HOURS)
        .build();
    //        servlet.registerGet("/devforum/:type/:postId", new GetTextDevforumPost());

    public static HashMap<String, String> handbooks;
    static {
        handbooks = new HashMap <>();
        handbooks.put("PBST", "7335071");
        handbooks.put("PET", "6747139");
        handbooks.put("TMS", "7410270");
        handbooks.put("PB", "7413565");
    }

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
        JSONObject root = new JSONObject();
        //String type = request.params("type");
        //String postId = request.params("postId");


        for (Map.Entry <String, String> entry : handbooks.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            JSONObject postInfo = getPostInformation(key, value);
            if (postInfo == null) {
                postInfo = new JSONObject();
                postInfo.put("error", true);
            } else {
                postInfo.put("error", false);
            }

            root.put(key, postInfo);
        }



        return root;
    }

    private JSONObject getPostInformation(String type, String postId) {
        JSONObject cacheObject = cache.getIfPresent(type + ":" + postId);

        /*if (cacheObject != null) {
            cacheObject.put("cached", true);
            return cacheObject;
        }*/

        okhttp3.Request.Builder request = new okhttp3.Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://devforum.roblox.com/posts/" + postId + ".json");

        //assertLogins(apiKey, request);

        try (okhttp3.Response response = Xeus.getInstance().getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                if (response.body()== null) return null;

                JSONObject finalObject = new JSONObject(response.body().string());
                finalObject.put("cached", false);
                //cache.put(type + ":" + postId, finalObject);
                return finalObject;
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
