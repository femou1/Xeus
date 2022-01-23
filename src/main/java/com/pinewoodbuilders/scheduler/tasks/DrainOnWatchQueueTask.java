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
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.moderation.local.watch.WatchContainer;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.modlog.local.watchlog.Watchlog;
import com.pinewoodbuilders.scheduler.ScheduleHandler;
import com.pinewoodbuilders.time.Carbon;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DrainOnWatchQueueTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DrainOnWatchQueueTask.class);

    @Override
    public void handle(Xeus avaire) {
        if (avaire.getOnWatchManger() == null || avaire.getOnWatchManger().getOnWatchs().isEmpty()) {
            return;
        }

        for (Map.Entry<Long, HashSet<WatchContainer>> entry : avaire.getOnWatchManger().getOnWatchs().entrySet()) {
            for (WatchContainer container : entry.getValue()) {
                if (container.isPermanent() || container.getSchedule() != null) {
                    continue;
                }

                Carbon expires = container.getExpiresAt();

                //noinspection ConstantConditions
                if (expires.copy().subMinutes(5).isPast()) {
                    long differenceInSeconds = expires.getTimestamp() - getCurrentTimestamp();
                    if (differenceInSeconds < 1) {
                        differenceInSeconds = 1;
                    }

                    log.debug("Unwatch task started for guildId:{}, userId:{}, time:{}",
                        container.getGuildId(), container.getUserId(), differenceInSeconds
                    );

                    container.setSchedule(ScheduleHandler.getScheduler().schedule(
                        () -> handleAutomaticUnmute(avaire, container),
                        differenceInSeconds,
                        TimeUnit.SECONDS
                    ));
                }
            }
        }
    }

    private long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    private void handleAutomaticUnmute(Xeus avaire, WatchContainer container) {
        try {
            Guild guild = avaire.getShardManager().getGuildById(container.getGuildId());
            if (guild == null) {
                if (avaire.areWeReadyYet()) {
                    unregisterDatabaseRecord(avaire, container);
                }

                container.cancelSchedule();
                return;
            }

            unregisterDatabaseRecord(avaire, container);

            Member member = guild.getMemberById(container.getUserId());
            if (member == null) {
                return;
            }


            GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guild);
    
            GuildTransformer transformer = GuildController.fetchGuild(avaire, guild);
            if (transformer == null || settings == null || settings.getOnWatchRole() == 0) {
                return;
            }


            Role muteRole = guild.getRoleById(settings.getOnWatchRole());
            if (muteRole == null) {
                return;
            }

            guild.removeRoleFromMember(
                member, muteRole
            ).queueAfter(1, TimeUnit.SECONDS, aVoid -> {
                log.debug("Successfully removed the {} role from {} on the {} server.",
                    muteRole.getName(), member.getUser().getAsTag(), guild.getName()
                );

                ModlogAction watchAction = new ModlogAction(
                    ModlogType.UN_ON_WATCH, guild.getSelfMember().getUser(), member.getUser(),
                    I18n.getString(guild, "onwatch.UnWatchCommand.userAutoUnmutedReason")
                );

                String caseId = Watchlog.log(avaire, guild, transformer, watchAction, settings);

                Watchlog.notifyUser(member.getUser(), guild, watchAction, caseId);


            }, throwable -> {
                log.debug("Failed to remove role from {} on the {} guild, error: {}",
                    container.getUserId(), container.getGuildId(), throwable.getMessage(), throwable
                );
            });
        } catch (Exception e) {
            log.error("Something went wrong in the auto unmute: {}", e.getMessage(), e);
        }
    }

    private void unregisterDatabaseRecord(Xeus avaire, WatchContainer container) {
        try {
            avaire.getOnWatchManger().unregisterOnWatch(container.getGuildId(), container.getUserId());
        } catch (SQLException e) {
            log.error("Failed to unregister mute for guildId:{}, userId:{}",
                container.getGuildId(), container.getUserId(), e
            );
        }
    }
}
