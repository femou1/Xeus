/*
 * Copyright (c) 2018.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.modlog.global.shared;

import com.google.common.base.CaseFormat;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.language.I18n;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;

public enum GlobalModlogType {

    /**
     * Represents the action when a user is kicked from a server.
     */
    GLOBAL_KICK(1, "\uD83D\uDC62", true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is banned from a server and gets a unban after
     * a certain amount of time. Specified in the ban.
     */
    GLOBAL_TEMP_BAN(2, "\uD83D\uDD28", true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is banned from a server, including
     * deleting any message they have sent in the last 7 days.
     */
    GLOBAL_BAN(3, "\uD83D\uDD28", true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is warned through Avas warn system.
     */
    GLOBAL_WARN(4, "\uD83D\uDCE2", true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is unbanned from a server through Ava.
     */
    GLOBAL_UNBAN(5, null, false, false, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is pardoned for an old modlog case.
     */
    GLOBAL_PARDON(6, null, true, false, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is muted from a server.
     */
    GLOBAL_MUTE(7, "\uD83D\uDD07", true, true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is muted temporarily from a server.
     */
    GLOBAL_TEMP_MUTE(8, "\uD83D\uDD07", true, true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is unmuted in a server.
     */
    GLOBAL_UNMUTE(9, "\uD83D\uDD0A", true, false, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is watched on a server.
     */
    GLOBAL_WATCH(10, "\uD83D\uDC53", true, true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is watched temporarily from a server.
     */
    GLOBAL_TEMP_WATCH(11, "\uD83D\uDC53", true, true, MessageType.MGM_PINEWOOD.getColor()),

    /**
     * Represents when a user is unwatched in a server.
     */
    GLOBAL_UN_WATCH(12, "\uD83D\uDC53", true, false, MessageType.MGM_PINEWOOD.getColor());


    final int id;
    @Nullable
    final String emote;
    final boolean notifyable;
    final boolean punishment;
    final Color color;

    GlobalModlogType(int id, String emote, boolean notifyable, Color color) {
        this(id, emote, notifyable, true, color);
    }

    GlobalModlogType(int id, @Nullable String emote, boolean notifyable, boolean punishment, Color color) {
        this.id = id;
        this.emote = emote;
        this.notifyable = notifyable;
        this.punishment = punishment;
        this.color = color;
    }

    /**
     * Tries to get a modlog type by ID, if no modlog types exists with
     * the given ID, <code>null</code> will be returned instead.
     *
     * @param id The ID that the modlog type should have.
     * @return Possibly-null, the modlog tpe with the given ID.
     */
    public static GlobalModlogType fromId(int id) {
        for (GlobalModlogType type : values()) {
            if (type.getId() == id) {
                return type;
            }
        }
        return null;
    }

    /**
     * Gets the ID of the current modlog type.
     *
     * @return The ID of the current modlog type.
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the name of the current modlog type.
     *
     * @param guild The guild that requested the modlog type name.
     * @return The name of the current modlog type.
     */
    public String getName(GlobalSettingsTransformer guild) {
        if (guild == null) {return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name())
            .replaceAll("(.)([A-Z])", "$1 $2");}

        String name = loadNameProperty(guild, "name");
        if (name != null) {
            return name;
        }

        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name())
            .replaceAll("(.)([A-Z])", "$1 $2");
    }

    /**
     * Gets the notify name for the current modlog type.
     *
     * @param guild The guild that requested the modlog type name.
     * @return The notify name for the current modlog type.
     */
    @Nullable
    public String getNotifyName(GlobalSettingsTransformer guild) {
        if (!notifyable) {
            return null;
        }
        return loadNameProperty(guild, "action");
    }

    /**
     * Gets the emote that is associated with the modlog type.
     *
     * @return The emote that is associated with the modlog type.
     */
    @Nonnull
    public String getEmote() {
        return emote == null ? "" : emote;
    }

    /**
     * Gets the color of the current modlog type.
     *
     * @return The color of the current modlog type.
     */
    public Color getColor() {
        return color;
    }

    /**
     * Checks if the current modlog type is a punishment or not.
     *
     * @return <code>True</code> if the modlog type is a punishment, <code>False</code> otherwise.
     */
    public boolean isPunishment() {
        return punishment;
    }

    private String loadNameProperty(GlobalSettingsTransformer guild, String type) {
        System.out.println(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name()));
        return I18n.getString(Xeus.getInstance().getShardManager().getGuildById(guild.getAppealsDiscordId()),
            String.format("global-modlog-types.%s.%s",
                CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name()),
            type
        ));
    }
}
