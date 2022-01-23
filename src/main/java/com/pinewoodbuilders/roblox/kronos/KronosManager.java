package com.pinewoodbuilders.roblox.kronos;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.contracts.kronos.TrellobanLabels;
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.requests.service.kronos.trelloban.TrellobanService;
import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Card;
import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Datum;
import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Label;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.utilities.NumberUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class KronosManager {

    private final Xeus avaire;
    private final RobloxAPIManager manager;
    protected final String apikey;
    private final String evalApiKey;

    public KronosManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
        this.apikey = avaire.getConfig().getString("apiKeys.kronosDatabaseApiKey");
        this.evalApiKey = avaire.getConfig().getString("apiKeys.kronosDatabaseEvalsApiKey");
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
    
    public Object modifyEvalStatus(Long userId, String division, boolean status) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", evalApiKey);

        if (status) {
            request.url("https://pb-kronos.dev/api/v2/database/"+division+"/eval/add/" + userId);
        } else {
            request.url("https://pb-kronos.dev/api/v2/database/"+division+"/eval/delete/" + userId);
        }

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();
                return new JSONObject(body);
            } else if (response.code() == 404) {
                return new JSONObject();
            } else if (response.code() == 501) {
                return new JSONObject("[{\"error\": \"Eval status could not be set.\"}]");
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


    public HashMap <Long, List<TrellobanLabels>> getTrelloBans(long mainGroupId) {
        String cache = (String) avaire.getCache().getAdapter(CacheType.FILE).get("trelloban.global." + mainGroupId);
        HashMap<Long, List<TrellobanLabels>> root = new HashMap<>();
        if (cache != null) {
            TrellobanService tbs = (TrellobanService) avaire.getRobloxAPIManager().toService(cache, TrellobanService.class);
            //System.out.println(avaire.getCache().getAdapter(CacheType.FILE).get("trelloban.global"));
            //System.out.println(tbs.getLoaded());

            createTrelloBanList(root, tbs);

        } else {
            RequestFactory.makeGET("https://pb-kronos.dev/api/v2/moderation/admin")
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosTrellobanKey"))
                .send((Consumer <Response>) response -> {

                    try {
                        TrellobanService tbs = (TrellobanService) avaire.getRobloxAPIManager().toService((String) response.body().string(), TrellobanService.class);

                        createTrelloBanList(root, tbs);

                        avaire.getCache().getAdapter(CacheType.FILE).remember("trelloban.global." + mainGroupId, (60*60)*90, () -> {
                            try {
                                return response.body().string();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        });
                    } catch (IOException ignored) {
                        return;
                    }
                });
        }
        return root;
    }

    public HashMap <Long, List<TrellobanLabels>> getTrelloBans() {
        return getTrelloBans(159511L);
    }

    private void createTrelloBanList(HashMap <Long, List <TrellobanLabels>> root, TrellobanService tbs) {
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

                    List <TrellobanLabels> labels = new ArrayList <>();
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
    }

}
