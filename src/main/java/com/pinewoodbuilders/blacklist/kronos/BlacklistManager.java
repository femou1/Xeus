package com.pinewoodbuilders.blacklist.kronos;

import com.pinewoodbuilders.Xeus;

import java.util.ArrayList;

public record BlacklistManager(Xeus avaire) {

    public ArrayList <Long> getTMSBlacklist() {
        return avaire.getRobloxAPIManager().getKronosManager().getBlacklist("TMS");
    }

    public ArrayList <Long> getPETBlacklist() {
        return avaire.getRobloxAPIManager().getKronosManager().getBlacklist("PET");
    }

    public ArrayList <Long> getPBSTBlacklist() {
        return avaire.getRobloxAPIManager().getKronosManager().getBlacklist("PBST");
    }

    public ArrayList <Long> getPBMBlacklist() {
        return avaire.getRobloxAPIManager().getKronosManager().getBlacklist("PBM");
    }

    public boolean isAnyBlacklisted(Long userId) {
        return avaire.getRobloxAPIManager().getKronosManager().getBlacklist("PBM").contains(userId) ||
                avaire.getRobloxAPIManager().getKronosManager().getBlacklist("PBST").contains(userId) ||
                avaire.getRobloxAPIManager().getKronosManager().getBlacklist("PET").contains(userId) ||
                avaire.getRobloxAPIManager().getKronosManager().getBlacklist("TMS").contains(userId);
    }

}
