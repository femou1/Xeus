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

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.moderation.local.warn.WarnContainer;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.scheduler.ScheduleHandler;
import com.pinewoodbuilders.time.Carbon;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DrainWarnQueueTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DrainWarnQueueTask.class);

    @Override
    public void handle(Xeus avaire) {
        if (avaire.getWarningsManager() == null || avaire.getWarningsManager().getWarns().isEmpty()) {
            return;
        }

        for (Map.Entry <Long, HashMap <Long, HashSet <WarnContainer>>> guild : avaire.getWarningsManager().getWarns().entrySet()) {
            for (Map.Entry <Long, HashSet <WarnContainer>> userWarns : guild.getValue().entrySet()) {
                for (WarnContainer container : userWarns.getValue()) {
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

                        log.debug("Unwarn task started for guildId:{}, userId:{}, time:{}",
                            container.getGuildId(), container.getUserId(), differenceInSeconds
                        );

                        container.setSchedule(ScheduleHandler.getScheduler().schedule(
                            () -> handleAutomaticUnwarn(avaire, container),
                            differenceInSeconds,
                            TimeUnit.SECONDS
                        ));
                    }
                }
            }
        }
    }

    private long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    private void handleAutomaticUnwarn(Xeus avaire, WarnContainer container) {
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

            Collection collection = avaire.getDatabase().newQueryBuilder(Constants.LOG_TABLE_NAME)
                .where("guild_id", guild.getId())
                .where("modlogCase", container.getCaseId())
                .get();

            if (collection.isEmpty()) {
                return;
            }

            GuildTransformer transformer = GuildController.fetchGuild(avaire, guild);
            if (transformer == null) {
                return;
            }

            ModlogAction modlogAction = new ModlogAction(
                ModlogType.PARDON, guild.getSelfMember().getUser(), member.getUser(),
                container.getCaseId() + ":" + collection.get(0).getString("message_id") + "\n" +
                "Warn has expired | This warn has been pardoned, and doesn't count against the warn total anymore. (Any punishments given by mods do still apply)"
            );

            String caseId = Modlog.log(avaire, guild, transformer, modlogAction);
            Modlog.notifyUser(member.getUser(), guild, modlogAction, caseId);
        } catch (Exception e) {
            log.error("Something went wrong in the auto unwarn: {}", e.getMessage(), e);
        }
    }

    private void unregisterDatabaseRecord(Xeus avaire, WarnContainer container) {
        try {
            avaire.getWarningsManager().unregisterWarn(container.getGuildId(), container.getUserId(), container.getCaseId());
        } catch (SQLException e) {
            log.error("Failed to unregister warn for guildId:{}, userId:{}",
                container.getGuildId(), container.getUserId(), e
            );
        }
    }
}
