package com.pinewoodbuilders.contracts.appeals;

import com.pinewoodbuilders.chat.MessageType;

import javax.annotation.Nullable;
import java.awt.*;

public enum AppealType {
    TRELLOBAN(1, "<:trelloban:997965939792412823>", "Trello Ban", false, MessageType.MGM_PINEWOOD.getColor()),
    GAMEBAN(2, "<:gameban:997965983153147955>", "Game ban", false, MessageType.MGM_PINEWOOD.getColor()),
    GAMEMUTE(3, "<:gamemute:997966017143775252>", "Game mute", false, MessageType.MGM_PINEWOOD.getColor()),
    GLOBALBAN(4, "<:globalban:997966041277804546>", "Global ban", false, MessageType.MGM_PINEWOOD.getColor()),
    RAIDBLACKLIST(5, "<:raidblacklist:997966060542230538>", "Raid blacklist", false, MessageType.INFO.getColor()),
    GROUPRANKLOCK(6, null, "Ranklock", true, MessageType.INFO.getColor()),
    GROUPDISCORDBAN(7, null, "Discord Ban", true, MessageType.INFO.getColor()),
    GROUPBLACKLIST(8, null, "Blacklist", true, MessageType.INFO.getColor());


    final int id;
    @Nullable
    final String emote;
    final boolean guildSelect;
    final Color color;
    final String name;
    final String cleanName;
    AppealType(int id, @Nullable String emote, String cleanName, boolean guildSelect, Color color) {
        this.id = id;
        this.emote = emote;
        this.guildSelect = guildSelect;
        this.color = color;
        this.cleanName = cleanName;
        this.name = name().toLowerCase();
    }

    public static  AppealType fromName(String named) {
        for (AppealType type : values()) {
            if (type.getName().equals(named.toLowerCase())) {
                return type;
            }
        }
        return null;
    }

    public static AppealType fromId(int id) {
        for (AppealType type : values()) {
            if (type.getId() == id) {
                return type;
            }
        }
        return null;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getEmote() {
        return emote;
    }

    public boolean isGuildSelect() {
        return guildSelect;
    }

    public Color getColor() {
        return color;
    }

    public String getCleanName() {
        return cleanName;
    }
}
