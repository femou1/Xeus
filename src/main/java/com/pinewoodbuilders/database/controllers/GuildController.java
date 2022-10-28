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

package com.pinewoodbuilders.database.controllers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.CacheUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuildController {

    public static final Cache<Long, GuildTransformer> cache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build();

    private static final Logger log = LoggerFactory.getLogger(GuildController.class);

    private static final String[] requiredGuildColumns = new String[]{
        "guild_types.name as type_name", "guild_types.limits as type_limits", "guilds.id", "guilds.partner", "guilds.name", "guilds.icon",
        "guilds.local", "guilds.channels", "guilds.modules", "guilds.level_roles", "guilds.level_modifier", "guilds.claimable_roles",
        "guilds.prefixes", "guilds.aliases", "guilds.level_exempt_channels", "guilds.level_exempt_roles", "guilds.modlog_case", "guilds.modlog",
        "guilds.mute_role", "guilds.autorole", "guilds.level_channel", "guilds.level_alerts", "guilds.levels", "guilds.hierarchy",
        "guilds.on_watch_case", "guilds.lockable_channels_roles", "guilds.lockable_channels", "guilds.auto_channel", "guilds.command_admin_channels",
        "guilds.ignored_audit_log_channels"
    };

    /**
     * Fetches the guild transformer from the cache, if it doesn't exist in the
     * cache it will be loaded into the cache and then returned afterwords.
     *
     * @param avaire  The avaire instance, used to talking to the database.
     * @param message The JDA message instance for the current message.
     * @return Possibly null, the guild transformer instance for the current guild, or null.
     */
    @CheckReturnValue
    public static GuildTransformer fetchGuild(Xeus avaire, Message message) {
        if (!message.getChannelType().isGuild()) {
            return null;
        }
        return fetchGuild(avaire, message.getGuild());
    }

    /**
     * Fetches the guild transformer from the cache, if it doesn't exist in the
     * cache it will be loaded into the cache and then returned afterwords.
     *
     * @param avaire The avaire instance, used to talking to the database.
     * @param guild  The JDA guild instance for the current guild.
     * @return Possibly null, the guild transformer instance for the current guild, or null.
     */
    @CheckReturnValue
    public static GuildTransformer fetchGuild(Xeus avaire, Guild guild) {
        return (GuildTransformer) CacheUtil.getUncheckedUnwrapped(cache, guild.getIdLong(), () -> loadGuildFromDatabase(avaire, guild));
    }

    public static String buildChannelData(List <GuildChannel> textChannels) {
        List <Map <String, Object>> channels = new ArrayList <>();
        for (GuildChannel channel : textChannels) {
            Map <String, Object> item = new HashMap <>();

            item.put("id", channel.getId());
            item.put("name", channel.getName());
            if (channel instanceof TextChannel tc) item.put("position", tc.getPosition());
            if (channel instanceof VoiceChannel vc) item.put("position", vc.getPosition());

            channels.add(item);
        }
        return Xeus.gson.toJson(channels);
    }

    public static String buildRoleData(List<Role> roles) {
        List<Map<String, Object>> rolesMap = new ArrayList<>();
        for (Role role : roles) {
            if (role.isPublicRole()) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();

            item.put("id", role.getId());
            item.put("name", role.getName());
            item.put("position", role.getPosition());
            item.put("permissions", role.getPermissionsRaw());

            item.put("color", role.getColor() == null ? null
                : Integer.toHexString(role.getColor().getRGB()).substring(2)
            );

            rolesMap.add(item);
        }
        return Xeus.gson.toJson(rolesMap);
    }

    public static void forgetCache(long guildId) {
        cache.invalidate(guildId);
    }

    private static GuildTransformer loadGuildFromDatabase(Xeus avaire, Guild guild) {
        if (log.isDebugEnabled()) {
            log.debug("Guild cache for " + guild.getId() + " was refreshed");
        }
        try {
            GuildTransformer transformer = new GuildTransformer(guild, avaire.getDatabase()
                .newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .select(requiredGuildColumns)
                .leftJoin("guild_types", "guilds.type", "guild_types.id")
                .where("guilds.id", guild.getId())
                .get().first());

                if (!transformer.hasData()) {
                    guild.retrieveOwner().queue(
                        member -> updateGuildEntry(avaire, guild, member),
                        throwable -> updateGuildEntry(avaire, guild, null)
                    );
    
                    return new GuildTransformer(guild);
                }

            return transformer;
        } catch (Exception ex) {
            log.error("Failed to fetch guild transformer from the database, error: {}", ex.getMessage(), ex);

            return null;
        }
    }

    private static void updateGuildEntry(Xeus avaire, Guild guild, Member owner) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .insert(statement -> {
                    statement
                        .set("id", guild.getId())
                        .set("owner", owner == null ? 0 : owner.getIdLong())
                        .set("name", guild.getName(), true)
                        .set("roles_data", buildRoleData(guild.getRoles()), true)
                        .set("channels_data", buildChannelData(guild.getChannels()), true);

                    if (guild.getIconId() != null) {
                        statement.set("icon", guild.getIconId());
                    }
                });
        } catch (Exception ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }
    }
}
