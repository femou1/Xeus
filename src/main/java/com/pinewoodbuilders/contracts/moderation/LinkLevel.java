package com.pinewoodbuilders.contracts.moderation;

import java.awt.*;

public enum LinkLevel {
    SAFE(10, false, false, false, new Color(0, 255, 0)),
    WARN(5, false, true, false, new Color(255, 171, 46)),
    SHORT(3, false, true, true, new Color(46, 81, 255)),
    DELETE(0, true, true, false, new Color(255, 0, 0));


    private final int level;
    private final boolean delete;
    private final boolean warn;
    private final Color color;
    private final boolean checkRedirect;

    LinkLevel(int level, boolean delete, boolean warn, boolean checkRedirect, Color color) {
        this.level = level;
        this.delete = delete;
        this.warn = warn;
        this.color = color;
        this.checkRedirect = checkRedirect;
    }

    public int getLevel() {
        return level;
    }

    public boolean isDelete() {
        return delete;
    }

    public boolean isWarn() {
        return warn;
    }

    public Color getColor() {return color;}

    public boolean isCheckRedirect() {
        return checkRedirect;
    }

    public static LinkLevel getLinkLevelFromId(int lvl){
        for(LinkLevel level : LinkLevel.values()){
            if (lvl == level.getLevel()) return level;
        }
        return DELETE;
    }
}
