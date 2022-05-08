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
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.CacheUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.concurrent.TimeUnit;

public class GuildSettingsController {

    public static final Cache <Long, GuildSettingsTransformer> cache = CacheBuilder.newBuilder().recordStats()
        .expireAfterAccess(5, TimeUnit.MINUTES).build();

    private static final Logger log = LoggerFactory.getLogger(GuildSettingsController.class);

    private static final String[] requiredSettingsColumns = new String[]{
            "guild_settings.id",
            "guild_settings.roblox_group_id", "guild_settings.group_name", "guild_settings.main_group_id",
            "guild_settings.main_discord_role", "guild_settings.minimum_hr_rank", "guild_settings.minimum_lead_rank",
            "guild_settings.admin_roles", "guild_settings.manager_roles", "guild_settings.moderator_roles", "guild_settings.group_shout_roles",
            "guild_settings.pb_verification_trelloban", "guild_settings.pb_verification_blacklist_link",
            "guild_settings.verification_anti_main_global_mod_impersonate", "guild_settings.permission_bypass",
            "guild_settings.emoji_id", "guild_settings.on_watch_channel", "guild_settings.on_watch_role",
            "guild_settings.local_filter", "guild_settings.local_filter_log", "guild_settings.patrol_remittance_channel",
            "guild_settings.patrol_remittance_message", "guild_settings.handbook_report_channel",
            "guild_settings.suggestion_channel_id", "guild_settings.suggestion_community_channel_id",
            "guild_settings.suggestion_approved_channel_id", "guild_settings.join_logs",
            "guild_settings.global_ban", "guild_settings.global_kick",
            "guild_settings.global_verify", "guild_settings.global_anti_unban", "guild_settings.global_automod",
            "guild_settings.automod_mass_mention", "guild_settings.automod_emoji_spam",
            "guild_settings.automod_link_spam", "guild_settings.automod_message_spam",
            "guild_settings.automod_image_spam", "guild_settings.automod_character_spam",
            "guild_settings.audit_logs_channel_id", "guild_settings.vote_validation_channel_id",
            "guild_settings.user_alerts_channel_id", "guild_settings.evaluation_answer_channel",
            "guild_settings.eval_questions", "guild_settings.handbook_report_info_message", "guild_settings.official_sub_group",
            "guild_settings.link_filter_log", "guild_settings.reward_request_channel_id", "guild_settings.leadership_server",
    };

    /**
     * Fetches the guild transformer from the cache, if it doesn't exist in the
     * cache it will be loaded into the cache and then returned afterwords.
     *
     * @param avaire  The avaire instance, used to talking to the database.
     * @param message The JDA message instance for the current message.
     * @return Possibly null, the guild transformer instance for the current guild,
     * or null.
     */
    @CheckReturnValue
    public static GuildSettingsTransformer fetchGuild(Xeus avaire, Message message) {
        if (!message.getChannelType().isGuild()) {
            return null;
        }
        return fetchGuildSettingsFromGuild(avaire, message.getGuild());
    }

    /**
     * Fetches the guild transformer from the cache, if it doesn't exist in the
     * cache it will be loaded into the cache and then returned afterwords.
     *
     * @param avaire The avaire instance, used to talking to the database.
     * @param guild  The JDA guild instance for the current guild.
     * @return Possibly null, the guild transformer instance for the current guild,
     * or null.
     */
    @CheckReturnValue
    public static GuildSettingsTransformer fetchGuildSettingsFromGuild(Xeus avaire, Guild guild) {
        return (GuildSettingsTransformer) CacheUtil.getUncheckedUnwrapped(cache, guild.getIdLong(),
            () -> loadGuildSettingsFromDatabase(avaire, guild));
    }

    public static void forgetCache(long guildId) {
        cache.invalidate(guildId);
    }

    private static GuildSettingsTransformer loadGuildSettingsFromDatabase(Xeus avaire, Guild guild) {
        if (log.isDebugEnabled()) {
            log.debug("Settings cache for " + guild.getId() + " was refreshed");
        }
        try {
            GuildSettingsTransformer transformer = new GuildSettingsTransformer(
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .select(requiredSettingsColumns).where("guild_settings.id", guild.getId()).get()
                    .first());

            if (!transformer.hasData()) {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).insert(statement -> {
                    statement.set("id", guild.getId());
                });


                return new GuildSettingsTransformer(avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .select(requiredSettingsColumns).where("guild_settings.id", guild.getId()).get()
                    .first());
            }

            return transformer;
        } catch (Exception ex) {
            log.error("Failed to fetch guild transformer from the database, error: {}", ex.getMessage(), ex);

            return null;
        }
    }
}
