package com.pinewoodbuilders.contracts.moderation;

import com.pinewoodbuilders.time.Carbon;

public enum WarningGrade {

    THREE_WARNINGS(3, true, false, false, false, Carbon.now().addDays(3), "gmute"),
    SIX_WARNINGS(6, true, true, false, false, Carbon.now().addWeeks(1), "gwatchmute"),
    NINE_WARNINGS(9, true, true, false, false, Carbon.now().addWeeks(2), "gwatchmute"),
    TWELVE_WARNINGS(12, false, true, true, false, null, "lbangwatch"),
    FIFTEEN_WARNINGS(15, false, false, false, true, null, "gban");


    private final int warns;
    private final boolean global_mute;
    private final boolean global_watch;
    private final boolean local_ban;
    private final boolean global_ban;
    private final Carbon time;
    private final String name;

    WarningGrade(int warns, boolean global_mute, boolean global_watch, boolean local_ban, boolean global_ban, Carbon time, String name) {
        this.warns = warns;
        this.global_mute = global_mute;
        this.global_watch = global_watch;
        this.local_ban = local_ban;
        this.global_ban = global_ban;
        this.time = time;
        this.name = name;
    }

    public int getWarns() {
        return warns;
    }

    public boolean isGlobalMute() {
        return global_mute;
    }

    public boolean isGlobalWatch() {
        return global_watch;
    }

    public boolean isLocalBan() {
        return local_ban;
    }

    public boolean isGlobalBan() {
        return global_ban;
    }

    public Carbon getTime() {
        return time;
    }

    public static WarningGrade getLabelFromWarns(int warns){
        for(WarningGrade grade : WarningGrade.values()){
            if(warns == grade.getWarns()) return grade;
            if (warns >= 16) return FIFTEEN_WARNINGS;
        }
        return null;
    }

    public static WarningGrade getLabelFromName(String name){
        for(WarningGrade grade : WarningGrade.values()){
            if(name.equals(grade.getName())) return grade;
        }
        return null;
    }

    public String getName() {
        return name;
    }

}
