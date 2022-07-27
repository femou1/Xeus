package com.pinewoodbuilders.contracts.appeals;

import com.pinewoodbuilders.chat.MessageType;

import javax.annotation.Nullable;
import java.awt.*;

public enum AppealType {
    TRELLOBAN(1, "<:trelloban:997965939792412823>", "trello ban", false, MessageType.MGM_PINEWOOD.getColor(), "https://cdn.discordapp.com/emojis/997965939792412823.png?size=512"),
    GAMEBAN(2, "<:gameban:997965983153147955>", "game ban", false, MessageType.MGM_PINEWOOD.getColor(), "https://cdn.discordapp.com/emojis/997965983153147955.png?size=512"),
    GAMEMUTE(3, "<:gamemute:997966017143775252>", "game mute", false, MessageType.MGM_PINEWOOD.getColor(), "https://cdn.discordapp.com/emojis/997966017143775252.png?size=512"),
    GLOBALBAN(4, "<:globalban:997966041277804546>", "global ban", false, MessageType.MGM_PINEWOOD.getColor(), "https://cdn.discordapp.com/emojis/997966041277804546.png?size=512"),
    RAIDBLACKLIST(5, "<:raidblacklist:997966060542230538>", "raid blacklist", false, MessageType.INFO.getColor(), "https://cdn.discordapp.com/emojis/997966060542230538.png?size=512"),
    GROUPRANKLOCK(6, "<:ranklock:998332416991170651>", "ranklock", true, MessageType.INFO.getColor(), "https://cdn.discordapp.com/emojis/998332416991170651.png?size=512"),
    GROUPDISCORDBAN(7, "<:groupdiscordban:998332587447681135>", "discord ban", true, MessageType.INFO.getColor(), "https://cdn.discordapp.com/emojis/998332587447681135.png?size=512"),
    GROUPBLACKLIST(8, "<:blacklist:998332444916858880>", "blacklist", true, MessageType.INFO.getColor(), "https://cdn.discordapp.com/emojis/998332444916858880.png?size=512"),
    OTHER(9, "<:CadetThinking:893602259693338665>", "support appeal", true, MessageType.WARNING.getColor(), "https://cdn.discordapp.com/emojis/893602259693338665.png?size=512"),
    DELETION(10, "<:cereal2:958119849958211664>", "data deletion", true, MessageType.ERROR.getColor(), "https://cdn.discordapp.com/emojis/958119849958211664.png?size=512");


    final int id;
    @Nullable
    final String emote;
    final boolean guildSelect;
    final Color color;
    final String name;
    final String cleanName;
    final String emoteImage;

    AppealType(int id, @Nullable String emote, String cleanName, boolean guildSelect, Color color, String emoteImage) {
        this.id = id;
        this.emote = emote;
        this.guildSelect = guildSelect;
        this.color = color;
        this.cleanName = cleanName;
        this.name = name().toLowerCase();
        this.emoteImage = emoteImage;
    }

    public static AppealType fromName(String named) {
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

    public String getEmoteImage() {
        return emoteImage;
    }
}
