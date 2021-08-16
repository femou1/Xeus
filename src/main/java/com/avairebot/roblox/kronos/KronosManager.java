package com.avairebot.roblox.kronos;

import com.avairebot.AppInfo;
import com.avairebot.AvaIre;
import com.avairebot.cache.CacheType;
import com.avairebot.contracts.kronos.TrellobanLabels;
import com.avairebot.requests.service.kronos.trelloban.TrellobanService;
import com.avairebot.requests.service.kronos.trelloban.trello.Card;
import com.avairebot.requests.service.kronos.trelloban.trello.Datum;
import com.avairebot.requests.service.kronos.trelloban.trello.Label;
import com.avairebot.roblox.RobloxAPIManager;
import com.avairebot.utilities.NumberUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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


    public HashMap <Long, List<TrellobanLabels>> getTrelloBans() {
        TrellobanService tbs = (TrellobanService) avaire.getRobloxAPIManager().toService((String) avaire.getCache().getAdapter(CacheType.FILE).get("trelloban.global"), TrellobanService.class);
        //System.out.println(avaire.getCache().getAdapter(CacheType.FILE).get("trelloban.global"));
        //System.out.println(tbs.getLoaded());

        HashMap<Long, List<TrellobanLabels>> root = new HashMap<>();
        for (Datum d : tbs.getData()) {
            if ((d.getId().equals("59eb2341cdaf6ca9203ab35f") && d.getName().equals("Banlist"))
                || (d.getId().equals("5bb6a2564f067782ad4e21e9") && d.getName().equals("Alt Banlist"))) {
                for (Card c : d.getCards()) {
                    if (!c.getName().contains(":")) {
                        continue;
                    }

                    if (c.getName().endsWith(":")) continue;

                    String userId = c.getName().split(":")[1].trim();
                    if (!NumberUtil.isNumeric(userId)) continue;

                    List<TrellobanLabels> labels = new ArrayList <>();
                    for (Label l : c.getLabels()) {
                        TrellobanLabels label = TrellobanLabels.getLabelFromId(l.getId());
                        if (label == null) {
                            continue;
                        }
                        labels.add(label);
                    }
                    root.put(Long.valueOf(userId), labels);
                }
            }
        }
        return root;
    }

}
