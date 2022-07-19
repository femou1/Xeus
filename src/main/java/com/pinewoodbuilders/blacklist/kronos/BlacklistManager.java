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


}
