package com.pinewoodbuilders.blacklist.kronos;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.google.gson.internal.LinkedTreeMap;
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.requests.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record BlacklistManager(Xeus avaire) {

    private static final Logger log = LoggerFactory.getLogger(BlacklistManager.class);

    @SuppressWarnings("unchecked")
    public ArrayList <Long> getTMSBlacklist() {
        ArrayList <Long> list = new ArrayList <>();
        String tmsCacheToken = "blacklist.tms.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(tmsCacheToken)) {
            List <LinkedTreeMap <String, Double>> items = (List <LinkedTreeMap <String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(tmsCacheToken);
            for (LinkedTreeMap <String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("pbm Blacklist has been requested.");
        } else {
            RequestFactory.makeGET("https://pb-kronos.dev/tms/blacklist")
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosApiKey"))
                .send((Consumer <Response>) response -> {
                    log.info("tms Blacklist has been requested and updated.");
                    List <LinkedTreeMap <String, Double>> service = (List <LinkedTreeMap <String, Double>>) response.toService(List.class);
                    for (LinkedTreeMap <String, Double> item : service) {
                        list.add(item.get("id").longValue());
                    }
                    avaire.getCache().getAdapter(CacheType.FILE).remember(tmsCacheToken, 60 * 60, () -> service);
                });
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public ArrayList <Long> getPETBlacklist() {
        ArrayList <Long> list = new ArrayList <>();
        String petCacheToken = "blacklist.pet.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(petCacheToken)) {
            List <LinkedTreeMap <String, Double>> items = (List <LinkedTreeMap <String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(petCacheToken);
            for (LinkedTreeMap <String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("pet Blacklist has been requested.");
        } else {
            RequestFactory.makeGET("https://pb-kronos.dev/pet/blacklist")
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosApiKey"))
                .send((Consumer <Response>) response -> {
                    log.info("pet Blacklist has been requested and updated.");
                    List <LinkedTreeMap <String, Double>> service = (List <LinkedTreeMap <String, Double>>) response.toService(List.class);
                    for (LinkedTreeMap <String, Double> item : service) {
                        list.add(item.get("id").longValue());
                    }
                    avaire.getCache().getAdapter(CacheType.FILE).remember(petCacheToken, 60 * 60, () -> service);
                });
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public ArrayList <Long> getPBSTBlacklist() {
        ArrayList <Long> list = new ArrayList <>();
        String PBSTCacheToken = "blacklist.pbst.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(PBSTCacheToken)) {
            List <LinkedTreeMap <String, Double>> items = (List <LinkedTreeMap <String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(PBSTCacheToken);
            for (LinkedTreeMap <String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("PBST Blacklist has been requested.");
        } else {
            RequestFactory.makeGET("https://pb-kronos.dev/pbst/blacklist")
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosApiKey"))
                .send((Consumer <Response>) response -> {
                    log.info("PBST Blacklist has been requested and updated.");
                    List <LinkedTreeMap <String, Double>> service = (List <LinkedTreeMap <String, Double>>) response.toService(List.class);
                    for (LinkedTreeMap <String, Double> item : service) {
                        list.add(item.get("id").longValue());
                    }
                    avaire.getCache().getAdapter(CacheType.FILE).remember(PBSTCacheToken, 60 * 60, () -> service);
                });
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public ArrayList <Long> getPBMBlacklist() {
        ArrayList <Long> list = new ArrayList <>();
        String pbmCacheToken = "blacklist.pbm.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(pbmCacheToken)) {
            List <LinkedTreeMap <String, Double>> items = (List <LinkedTreeMap <String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(pbmCacheToken);
            for (LinkedTreeMap <String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("pbm Blacklist has been requested.");
        } else {
            RequestFactory.makeGET("https://pb-kronos.dev/pbm/blacklist")
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosApiKey"))
                .send((Consumer <Response>) response -> {
                    log.info("pbm Blacklist has been requested and updated.");
                    List <LinkedTreeMap <String, Double>> service = (List <LinkedTreeMap <String, Double>>) response.toService(List.class);
                    for (LinkedTreeMap <String, Double> item : service) {
                        list.add(item.get("id").longValue());
                    }
                    avaire.getCache().getAdapter(CacheType.FILE).remember(pbmCacheToken, 60 * 60, () -> service);
                });
        }
        return list;
    }


}
