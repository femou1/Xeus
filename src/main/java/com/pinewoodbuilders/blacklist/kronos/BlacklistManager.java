package com.pinewoodbuilders.blacklist.kronos;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.google.gson.internal.LinkedTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BlacklistManager {

    private final Xeus avaire;

    private static final Logger log = LoggerFactory.getLogger(BlacklistManager.class);

    public BlacklistManager(Xeus avaire) {
        this.avaire = avaire;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<Long> getTMSBlacklist() {
        String TMSCacheToken = "blacklist.tms.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(TMSCacheToken)) {
            ArrayList<LinkedTreeMap<String, Double>> items = (ArrayList<LinkedTreeMap<String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(TMSCacheToken);

            ArrayList<Long> list = new ArrayList<>();

            for (LinkedTreeMap<String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("TMS Blacklist has been requested.");
            return list;
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    public ArrayList<Long> getPETBlacklist() {
        String TMSCacheToken = "blacklist.pet.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(TMSCacheToken)) {
            ArrayList<LinkedTreeMap<String, Double>> items = (ArrayList<LinkedTreeMap<String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(TMSCacheToken);

            ArrayList<Long> list = new ArrayList<>();

            for (LinkedTreeMap<String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("PET Blacklist has been requested.");
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<Long> getPBSTBlacklist() {
        String PBSTCacheToken = "blacklist.pbst.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(PBSTCacheToken)) {
            List<LinkedTreeMap<String, Double>> items = (List<LinkedTreeMap<String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(PBSTCacheToken);


            ArrayList<Long> list = new ArrayList<>();

            for (LinkedTreeMap<String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("PBST Blacklist has been requested.");
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<Long> getPBMBlacklist() {
        String PBSTCacheToken = "blacklist.pbm.blacklists";
        if (avaire.getCache().getAdapter(CacheType.FILE).has(PBSTCacheToken)) {
            List<LinkedTreeMap<String, Double>> items = (List<LinkedTreeMap<String, Double>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get(PBSTCacheToken);


            ArrayList<Long> list = new ArrayList<>();

            for (LinkedTreeMap<String, Double> item : items) {
                list.add(item.get("id").longValue());
            }
            //log.info("PBM Blacklist has been requested.");
            return list;
        }
        return null;
    }


}
