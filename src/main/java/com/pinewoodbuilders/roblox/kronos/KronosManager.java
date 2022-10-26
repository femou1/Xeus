package com.pinewoodbuilders.roblox.kronos;

import com.google.gson.internal.LinkedTreeMap;
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
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class KronosManager {

    private final Xeus avaire;
    private final RobloxAPIManager manager;
    protected final String apikey;
    private final String evalApiKey;
    private final String blacklistKey;
    private static final Logger log = LoggerFactory.getLogger(KronosManager.class);
    private final String weaponbanKey;

    public KronosManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
        this.apikey = avaire.getConfig().getString("apiKeys.kronosDatabaseApiKey");
        this.evalApiKey = avaire.getConfig().getString("apiKeys.kronosDatabaseEvalsApiKey");
        this.blacklistKey = avaire.getConfig().getString("apiKeys.kronosApiKey");
        this.weaponbanKey = avaire.getConfig().getString("apikeys.kronosWeaponbanKey");
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
            Xeus.getLogger().error("Failed sending request to Kronos Points API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    public boolean isRanklocked(Long userId) {
        return isRanklocked(userId, "pbst");
    }

    public boolean isRanklocked(Long userId, String division) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", apikey)
            .url("https://pb-kronos.dev/api/v2/users/" + userId + "/basic");

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();

                JSONObject jsonObject = new JSONObject(body);
                if (jsonObject.length() == 0) {
                    return false;
                }


                return jsonObject.getJSONObject(division.toLowerCase()).getBoolean("Ranklocked");
            } else if (response.code() == 404) {
                return false;
            } else {
                log.debug("Kronos API returned something else then 200 " + response.code() + ", please retry.");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Xeus.getLogger().error("Failed sending request to Kronos Ranklock API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasRanklockAnywhere(Long userId) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", apikey)
            .url("https://pb-kronos.dev/api/v2/users/" + userId + "/ranklocks");

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();

                JSONObject jsonObject = new JSONObject(body);
                if (jsonObject.length() == 0) {
                    return false;
                }

                for (String group : jsonObject.keySet()) {
                    if (jsonObject.getJSONObject(group).getBoolean("Ranklocked")) return true;
                }

                return false;
            } else if (response.code() == 404) {
                return false;
            } else {
                log.debug("Kronos API returned something else then 200 " + response.code() + ", please retry.");
                return false;
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Kronos any ranklock API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasNegativePointsAnywhere(Long userId) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", apikey)
            .url("https://pb-kronos.dev/api/v2/users/" + userId + "/basic");

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();

                JSONObject jsonObject = new JSONObject(body);
                if (jsonObject.length() == 0) {
                    return false;
                }

                for (String group : jsonObject.keySet()) {
                    if (jsonObject.getJSONObject(group).getBoolean("HasNegativePoints")) return true;
                }

                return false;
            } else if (response.code() == 404) {
                return false;
            } else {
                throw new Exception("Kronos basic API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Kronos any basic API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void modifyEvalStatus(Long userId, String division, boolean status) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .addHeader("Access-Key", evalApiKey);

        if (status) {
            request.url("https://pb-kronos.dev/api/v2/database/" + division + "/eval/add/" + userId);
        } else {
            request.url("https://pb-kronos.dev/api/v2/database/" + division + "/eval/delete/" + userId);
        }

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                String body = response.body().string();
                new JSONObject(body);
            } else if (response.code() == 404) {
                new JSONObject();
            } else if (response.code() == 501) {
                new JSONObject("[{\"error\": \"Eval status could not be set.\"}]");
            } else {
                throw new Exception("Kronos API returned something else then 200, 404 or 501, please retry.");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Kronos Evals API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList <Long> getBlacklist(String division) {
        ArrayList <Long> list = new ArrayList <>();
        String cacheToken = "blacklist."+division+".blacklists";
        log.debug("Getting blacklist for division: " + division);
        if (avaire.getCache().getAdapter(CacheType.FILE).has(cacheToken)) {
            log.debug("Found blacklist in cache for division: " + division);
            List <LinkedTreeMap <String, Double>> items = (List <LinkedTreeMap <String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(cacheToken);
            for (LinkedTreeMap <String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            log.debug("Found " + list.size() + " blacklist entries for division: " + division);
        } else {
            Request.Builder request = new Request.Builder()
                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                .addHeader("Access-Key", blacklistKey)
                .url("https://pb-kronos.dev/"+division.toLowerCase()+"/blacklist");

            try (okhttp3.Response response = manager.getClient().newCall(request.build()).execute()) {
                if (response.code() == 200 && response.body() != null) {
                    log.debug("Found blacklist in Kronos for division: " + division);
                    List <LinkedTreeMap <String, Double>> service =
                        (List <LinkedTreeMap <String, Double>>) manager.toService(response.body().string(), List.class);
                    for (LinkedTreeMap <String, Double> item : service) {
                        list.add(item.get("id").longValue());
                    }
                    log.debug("Found " + list.size() + " blacklist entries for division: " + division);
                    avaire.getCache().getAdapter(CacheType.FILE).remember(cacheToken, 60 * 15, () -> service);
                    log.debug("Cached blacklist for division: " + division);
                } else if (response.code() == 404) {
                    return list;
                } else {
                    throw new Exception("Kronos API returned something else then 200 or 400, please retry.");
                }
            } catch (IOException e) {
                Xeus.getLogger().error("Failed sending request to Kronos blacklist API: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }


    public HashMap <Long, List<TrellobanLabels>> getTrelloBans(long mainGroupId) {
        String cache = (String) avaire.getCache().getAdapter(CacheType.FILE).get("trelloban.global." + mainGroupId);
        HashMap<Long, List<TrellobanLabels>> root = new HashMap<>();
        log.debug("Getting trello bans for main group: " + mainGroupId);
        if (cache != null) {
            log.debug("Found trello bans in cache for main group: " + mainGroupId);
            TrellobanService tbs = (TrellobanService) avaire.getRobloxAPIManager().toService(cache, TrellobanService.class);

            createTrelloBanList(root, tbs);
            log.debug("Found " + root.size() + " trello bans for main group: " + mainGroupId);

        } else {
            log.debug("No trello bans found in cache for main group: " + mainGroupId);
            Request.Builder request = new Request.Builder()
                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosTrellobanKey"))
                .url("https://pb-kronos.dev/api/v2/moderation/admin");
            log.debug("Sending request to Kronos trello ban API");

            try (Response response = manager.getClient().newCall(request.build()).execute()) {
                if (response.code() == 200 && response.body() != null) {

                    log.debug("Found trello bans in Kronos for main group: " + mainGroupId);
                    String body = response.body().string();

                    TrellobanService tbs = (TrellobanService) avaire.getRobloxAPIManager().toService(body, TrellobanService.class);

                    createTrelloBanList(root, tbs);
                    log.debug("Found " + root.size() + " trello bans for main group: " + mainGroupId);
                    avaire.getCache().getAdapter(CacheType.FILE).remember("trelloban.global." + mainGroupId, (60 * 60) * 90, () -> body);
                    log.debug("Cached trello bans for main group: " + mainGroupId);
                } else if (response.code() == 404) {
                    return null;
                } else {
                    throw new Exception("Kronos API returned something else then 200, please retry.");
                }
            } catch (IOException e) {
                Xeus.getLogger().error("Failed sending request to Kronos trellobans API: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
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
