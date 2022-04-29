/*
 * Copyright (c) 2019.
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

package com.pinewoodbuilders.scheduler.tasks;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.moderation.local.ban.BanContainer;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.scheduler.ScheduleHandler;
import com.pinewoodbuilders.time.Carbon;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Guild.Ban;

import net.dv8tion.jda.api.entities.UserSnowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DrainUnbanQueueTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DrainUnbanQueueTask.class);

    @Override
    public void handle(Xeus avaire) {
        if (avaire.getBanManger() == null || avaire.getBanManger().getBans().isEmpty()) {
            return;
        }

        for (Map.Entry<Long, HashSet<BanContainer>> entry : avaire.getBanManger().getBans().entrySet()) {
            for (BanContainer container : entry.getValue()) {
                if (container.isPermanent() || container.getSchedule() != null) {
                    continue;
                }

                Carbon expires = container.getExpiresAt();
                // noinspection ConstantConditions
                if (expires.copy().subMinutes(5).isPast()) {
                    long differenceInSeconds = expires.getTimestamp() - getCurrentTimestamp();
                    if (differenceInSeconds < 1) {
                        differenceInSeconds = 1;
                    }

                    log.debug("Unmute task started for guildId:{}, userId:{}, time:{}", container.getGuildId(),
                            container.getUserId(), differenceInSeconds);

                    container.setSchedule(ScheduleHandler.getScheduler().schedule(
                            () -> handleAutomaticUnban(avaire, container), differenceInSeconds, TimeUnit.SECONDS));
                }
            }
        }
    }

    private long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    private void handleAutomaticUnban(Xeus avaire, BanContainer container) {
            Guild guild = avaire.getShardManager().getGuildById(container.getGuildId());
            if (guild == null) {
                if (avaire.areWeReadyYet()) {
                    unregisterDatabaseRecord(avaire, container);
                }

                container.cancelSchedule();
                return;
            }

            unregisterDatabaseRecord(avaire, container);

            guild.retrieveBan(UserSnowflake.fromId(container.getUserId())).queue(ban -> {
                handleBan(guild, ban, container);
            }, error -> {
                log.error("Unable to retrieve ban for userId:{}", container.getUserId());
            });
        
    }
    private void handleBan(Guild guild, Ban ban, BanContainer container) {
        guild.unban(ban.getUser()).queue(aVoid -> {
            ModlogAction modlogAction = new ModlogAction(ModlogType.UNBAN, guild.getSelfMember().getUser(), ban.getUser(),
                    "*Automatic unmute after the set time has elapsed*");

            Modlog.log(Xeus.getInstance(), guild, modlogAction);
        }, error -> {
            log.error("Unable to unban userId: {}", container.getUserId());
        });
    }
    

    private void unregisterDatabaseRecord(Xeus avaire, BanContainer container) {
        try {
            avaire.getBanManger().unregisterBan(container.getGuildId(), container.getUserId());
        } catch (SQLException e) {
            log.error("Failed to unregister ban for guildId:{}, userId:{}", container.getGuildId(),
                    container.getUserId(), e);
        }
    }
}
