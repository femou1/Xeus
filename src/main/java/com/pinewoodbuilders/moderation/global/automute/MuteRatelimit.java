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

package com.pinewoodbuilders.moderation.global.automute;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.blacklist.PunishmentLevel;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.middleware.ThrottleMiddleware;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.CacheUtil;
import com.pinewoodbuilders.utilities.RestActionUtil;
import net.dv8tion.jda.api.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MuteRatelimit {

    /**
     * This represents that amount of times the rate limit can
     * be hit within the {@link #hitTime ratelimit timeframe}
     * before the user is auto muted.
     */
    static final int hitLimit = 5;

    /**
     * This represents the amount of time in milliseconds that each
     * hit should be valid for, if all hit slots for a given user
     * is taken up, but one of the times exceeds the hit time
     * limit, the rate limit won't count it as exceeding
     * the rate limit.
     */
    static final long hitTime = 480 * 1000;

    /**
     * The cache loader for holding all the ratelimiter rates.
     */
    public static final LoadingCache<Long, Rate> cache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterWrite(hitTime, TimeUnit.MILLISECONDS)
        .build(CacheLoader.from(userId -> userId != null ? new Rate(userId) : null));

    /**
     * The slf4j logger instance.
     */
    private static final Logger log = LoggerFactory.getLogger(MuteRatelimit.class);

    /**
     * The punishment level holder, this m
     * 9ap holds all the users and their current
     * punishment level, with each offence, the punishment level(value) will go
     * up, increasing the time the user get auto-blacklisted for.
     */
    private static final Map<Long, Integer> punishments = new HashMap<>();

    /**
     * The punishment levels, each index of the levels list should be an
     * increasingly harsher punishment for repeating offenders.
     */
    private static final List<PunishmentLevel> levels = Arrays.asList(
        () -> Carbon.now().addMinutes(15),
        () -> Carbon.now().addMinutes(30),
        () -> Carbon.now().addHour(),
        () -> Carbon.now().addHours(6),
        () -> Carbon.now().addHours(12),
        () -> Carbon.now().addDay(),
        () -> Carbon.now().addDays(3),
        () -> Carbon.now().addDays(7),
        () -> Carbon.now().addDays(14),
        () -> Carbon.now().addDays(21),
        () -> Carbon.now().addDays(28),
        () -> Carbon.now().addMonth(),
        () -> Carbon.now().addMonths(6),
        () -> Carbon.now().addYear()
    );


    /**
     * Sends the blacklist message to the given use in a direct
     * message to let the user know that they have been added
     * to the blacklist automatically.
     *
     * @param user    The user that was blacklisted.
     * @param expires The carbon time instance for when the blacklist expires.
     */
    public static void sendMuteMessage(User user, Carbon expires) {
        user.openPrivateChannel().queue(channel -> {
            channel.sendMessageEmbeds(MessageFactory.createEmbeddedBuilder()
                .setColor(Color.decode("#A5306B"))
                .setTitle("Whoa there!", "https://xeus.pinewood-builders.com/")
                .setFooter("Expires", null)
                .setTimestamp(expires.getTime().toInstant())
                .setDescription("Looks like you're triggering my filter a bit too fast, I've muted you "
                    + "on all connected guilds.\n"
                    + "Your mute expires in ``" + expires.addSecond().diffForHumans(true) + "``, "
                    + "keep in mind repeating the behavior will get you muted for longer "
                    + "periods of time, eventually if you keep it up you will be permanently"
                ).build()
            ).queue();
        }, RestActionUtil.ignore);
    }

    /**
     * Sends the blacklist message to the given channel to let
     * the user/guild know that they have been added to the
     * blacklist automatically.
     *
     * @param channel The channel that the message should be sent to.
     * @param expires The carbon time instance for when the blacklist expires.
     */
    public void sendBlacklissendtMessage(MessageChannel channel, Carbon expires) {
        channel.sendMessageEmbeds(MessageFactory.createEmbeddedBuilder()
            .setColor(Color.decode("#A5306B"))
            .setTitle("Whoa there!", "https://xeus.pinewood-builders.com/")
            .setFooter("Expires", null)
            .setTimestamp(expires.getTime().toInstant())
            .setDescription("Looks like people on the server are using commands a bit too fast, "
                + "I've banned the server from using any commands, or earning any XP until everyone "
                + "clams down a bit.\n"
                + "The ban expires in " + expires.addSecond().diffForHumans(true) + ", "
                + "keep in mind repeating the behavior will get the server banned for longer "
                + "periods of time, eventually if you keep it up the server will be banned "
                + "from using any of my commands permanently."
            ).build()
        ).queue();
    }

    /**
     * His the blacklisting ratelimit, the type will determine if the
     * user or the guild with the given ID will hit the blacklist.
     * <p>
     * If the given ID has reached the  maximum number of hits within
     * the allowed timeframe, entity with the given ID will be auto
     * blacklisted for a certain amount of time, the time the
     * entity is blacklisted for depends on how many earlier
     * offense they have, all the punishment levels can be
     * seen in the {@link #levels punishments array}.
     *
     * @param type The type of throttle request that hit the mutelist.
     * @param id   The ID of the user or guild that should hit the ratelimit.
     * @param e
     * @return Possibly-null, the time object matching when the blacklist expires,
     *         or <code>null</code> if the user was not blacklisted.
     */
    @Nullable
    public static Carbon hit(ThrottleMiddleware.ThrottleType type, long id, Message e) {
        Rate rate = CacheUtil.getUncheckedUnwrapped(cache, id);
        if (rate == null) {
            // This should never happen, if it does we'll just return
            // null to not block any commands by a valid user.
            return null;
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (rate) {
            rate.hit();

            if (rate.getHits() < hitLimit) {
                return null;
            }
        }

        Long last = rate.getLast();

        // Checks if the user was blacklisted within the last two and half seconds,
        // the command handling process uses its own thread pool, because of that
        // it's possible to have two commands come in from the same user in a
        // very quick succession, instead of punishing the user twice, we
        // just cancel the blacklist hit here instead.
        if (last != null && last < System.currentTimeMillis() - 2500) {
            return null;
        }

        Carbon punishment = getPunishment(id);

        log.info("{}:{} has been detected to be added to the mute list for excessive filter triggers, it shall expire on {}.",
            type.getName(), id, punishment.toDayDateTimeString()
        );

        globalMuteUser(punishment, e);

        return punishment;
    }

    private static void globalMuteUser(Carbon punishment, Message context) {
        GuildSettingsTransformer selfTransformer = GuildSettingsController.fetchGuild(Xeus.getInstance(), context);
        if (selfTransformer == null || selfTransformer.getMainGroupId() == 0) return;

        List<Guild> gs = Xeus.getInstance().getRobloxAPIManager().getVerification().getGuildsByMainGroupId(selfTransformer.getMainGroupId());
        for (Guild g : gs) {
            GuildTransformer transformer = GuildController.fetchGuild(Xeus.getInstance(), g);
            if (transformer.getMuteRole() == null) {
                continue;
            }

            Role muteRole = g.getRoleById(transformer.getMuteRole());
            if (muteRole == null) {
                continue;
            }

            Member m = g.getMember(context.getAuthor());
            if (m == null) {
                continue;
            }

            ModlogType type = punishment.getTime() == null ? ModlogType.MUTE : ModlogType.TEMP_MUTE;
            final Carbon finalExpiresAt = punishment;
            g.addRoleToMember(
                m, muteRole
            ).reason("You have triggered the auto mod way to fast").queue(aVoid -> {
                ModlogAction modlogAction = new ModlogAction(
                    type, context.getAuthor(), context.getAuthor(),
                    finalExpiresAt.toDayDateTimeString() + " (" + finalExpiresAt.diffForHumans(true) + ")" + "\n" + "You have triggered the filter way to fast"
                );

                String caseId = Modlog.log(Xeus.getInstance(), context, modlogAction);
                

                try {
                    Xeus.getInstance().getMuteManger().registerMute(caseId, g.getIdLong(), context.getAuthor().getIdLong(), finalExpiresAt);
                } catch (SQLException e) {
                    Xeus.getLogger().error(e.getMessage(), e);
                }
            });
            
        }
        MuteRatelimit.sendMuteMessage(context.getAuthor(), punishment);
    }

    /**
     * Gets the punishment for the given user ID, as well as
     * increasing their punishment level in the process.
     *
     * @param userId The ID of the user that the punishment should be fetched for.
     * @return The Carbon instance with the punishment expire time.
     */
    private static Carbon getPunishment(long userId) {
        int level = punishments.getOrDefault(userId, -1) + 1;

        punishments.put(userId, level);

        return getPunishment(level);
    }

    /**
     * Gets the {@link Carbon} punishment object, representing
     * the time that the punishment should expire.
     *
     * @param level The level to get the punishment for.
     * @return The Carbon instance with the punishment expire time.
     */
    private static Carbon getPunishment(int level) {
        if (level < 0) {
            return levels.get(0).generateTime();
        }
        return levels.get(level >= levels.size() ? levels.size() - 1 : level).generateTime();
    }
}
