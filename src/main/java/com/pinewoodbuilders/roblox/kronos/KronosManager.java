package com.pinewoodbuilders.roblox.kronos;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.contracts.kronos.TrellobanLabels;
import com.pinewoodbuilders.requests.service.kronos.trelloban.TrellobanService;
import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Card;
import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Datum;
import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Label;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.utilities.NumberUtil;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class KronosManager {

    private final Xeus avaire;
    private final RobloxAPIManager manager;
    protected final String apikey;

    public KronosManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
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
            Xeus.getLogger().error("Failed sending request to Kronos API: " + e.getMessage());
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
            Xeus.getLogger().error("Failed sending request to Kronos API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");

    public JSONArray modifyEvalStatus(Long userId, String division, boolean status) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", apikey)
            .url("https://pb-kronos.dev/api/v2/database/eval/"+division+"/" + userId);

        if (status) {
            request.post(RequestBody.create(json, "[]"));
        } else {
            request.delete(RequestBody.create(json, "[]"));
        }

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();
                JSONArray array = new JSONArray(body);
                return array;
            } else if (response.code() == 404) {
                return new JSONArray(response.body().string());
            } else if (response.code() == 501) {
                return new JSONArray("[{\"error\": \"Eval status could not be set.\"}]");
            } else {
                throw new Exception("Kronos API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Kronos API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
