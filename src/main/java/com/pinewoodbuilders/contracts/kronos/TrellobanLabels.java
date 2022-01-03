package com.pinewoodbuilders.contracts.kronos;

public enum TrellobanLabels {

    ABUSIVE_BEHAVIOR("59eb227e1314a33999444924", "Abusive Behavior", false, true),
    EXPLOITING("5b4f8f8c6e8230b3e80ab21d", "Exploiting", true, true),
    BLACKLISTED_WITHOUT_APPEAL("5b4fa5a06314cd5ffcad4502", "Blacklisted (No Appeal)", true, false),
    ADMIN_IMPERSONATION("5bb8e796cd76fd7286417f08", "Admin Impersonation", true, false),
    INAPPROPRIATE_USERNAME("5b4f934782e643294c0fc24c", "Inappropriate Username", true, true),
    TIMED_BAN("5b4f8fbc2c14c60d64b544f4", "Timed Ban", false, true),
    UNKNOWN(null, "No cards found", true, true);


    private final String id;
    private final String name;
    private final boolean permban;
    private final boolean appealable;

    TrellobanLabels(String id, String name, boolean permanent, boolean appealable) {
        this.id = id;
        this.name = name;
        this.permban = permanent;
        this.appealable = appealable;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isAppealable() {
        return appealable;
    }

    public boolean isPermban() {
        return permban;
    }

    public static TrellobanLabels getLabelFromId(String id){
        for(TrellobanLabels e : TrellobanLabels.values()){
            if(id.equals(e.getId())) return e;
        }
        return null;
    }

    public static TrellobanLabels getLabelFromName(String name){
        for(TrellobanLabels e : TrellobanLabels.values()){
            if(name.equals(e.getName())) return e;
        }
        return null;
    }

    public static TrellobanLabels getLabelFromStringAndId(String id, String name){
        for(TrellobanLabels e : TrellobanLabels.values()){
            if(name.equals(e.getName()) && id.equals(e.getId())) return e;
        }
        return null;
    }

}
