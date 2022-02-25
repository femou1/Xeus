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
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GlobalSettingsController;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.moderation.global.globalmute.GlobalMuteContainer;
import com.pinewoodbuilders.modlog.global.moderation.GlobalModlog;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogAction;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogType;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.scheduler.ScheduleHandler;
import com.pinewoodbuilders.time.Carbon;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DrainGlobalMuteQueueTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DrainGlobalMuteQueueTask.class);

    @Override
    public void handle(Xeus avaire) {
        if (avaire.getGlobalMuteManager() == null || avaire.getGlobalMuteManager().getGlobalMutes().isEmpty()) {
            return;
        }

        for (Map.Entry<Long, HashSet<GlobalMuteContainer>> entry : avaire.getGlobalMuteManager().getGlobalMutes().entrySet()) {
            for (GlobalMuteContainer container : entry.getValue()) {
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

                    log.debug("Unmute task started for guildId:{}, userId:{}, time:{}",
                        container.getRanGuildId(), container.getUserId(), differenceInSeconds
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

    private void handleAutomaticUnmute(Xeus avaire, GlobalMuteContainer container) {
        try {
            List<Guild> guilds = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(avaire, container.getMainGroupId());
            if (guilds == null) {
                container.cancelSchedule();
                return;
            }

            unregisterDatabaseRecord(avaire, container);

            User u = avaire.getShardManager().getUserById(container.getUserId());
            if (u == null) return;
            if (container.getMainGroupId() == 0) return;

            GlobalSettingsTransformer globalSettings = GlobalSettingsController.fetchGlobalSettingsFromGroupSettings(avaire, container.getMainGroupId());
            if (globalSettings == null) return;


            for (Guild g : guilds) {
                Member member = g.getMember(u);
                if (member == null) {
                    continue;
                }

                GuildTransformer transformer = GuildController.fetchGuild(avaire, g);
                if (transformer == null || transformer.getMuteRole() == null) {
                    continue;
                }

                Role muteRole = g.getRoleById(transformer.getMuteRole());
                if (muteRole == null) {
                    continue;
                }

                g.removeRoleFromMember(
                    member, muteRole
                ).queueAfter(1, TimeUnit.SECONDS, aVoid -> {
                    log.debug("Successfully removed the {} role from {} on the {} server.",
                        muteRole.getName(), member.getUser().getAsTag(), g.getName()
                    );
                }, throwable -> {
                    log.debug("Failed to remove role from {} on the {} guild, error: {}",
                        container.getUserId(), container.getRanGuildId(), throwable.getMessage(), throwable
                    );
                });

                ModlogAction modlogAction = new ModlogAction(
                    ModlogType.UNMUTE, avaire.getSelfUser(), u,
                    "*Automatic global-unmute after the set time has elapsed*");
                Modlog.log(avaire, g, modlogAction);

            }




            GlobalModlogAction modlogAction = new GlobalModlogAction(
                GlobalModlogType.GLOBAL_UNMUTE, avaire.getSelfUser(), u,
                "*Automatic global-unmute after the set time has elapsed*");

            String caseId = GlobalModlog.log(avaire, globalSettings, modlogAction);
            GlobalModlog.notifyUser(u, globalSettings, modlogAction, caseId);
        } catch (Exception e) {
            log.error("Something went wrong in the auto unmute: {}", e.getMessage(), e);
        }
    }

    private void unregisterDatabaseRecord(Xeus avaire, GlobalMuteContainer container) {
        try {
            avaire.getGlobalMuteManager().unregisterGlobalMute(container.getMainGroupId(), container.getUserId(), container.getRanGuildId());
        } catch (SQLException e) {
            log.error("Failed to unregister mute for guildId:{}, userId:{}",
                container.getRanGuildId(), container.getUserId(), e
            );
        }
    }

    public List <Guild> getGuildsByMainGroupId(Xeus avaire, Long mainGroupId) throws SQLException {
        return getGuildsByMainGroupId(avaire, mainGroupId, false);
    }

    public List <Guild> getGuildsByMainGroupId(Xeus avaire, Long mainGroupId, boolean isOfficial) throws SQLException {
        if (!avaire.areWeReadyYet()) {
            return null;
        }
        Collection guildQuery = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
            .where("main_group_id", mainGroupId)
            .where(builder -> {if (isOfficial) {
                builder.where("official_sub_group", 1);
            }})
            .get();

        List <Guild> guildList = new LinkedList <>();
        for (DataRow dataRow : guildQuery) {
            Guild guild = avaire.getShardManager().getGuildById(dataRow.getString("id"));
            if (guild != null) {
                guildList.add(guild);
            }
        }

        return guildList;
    }
}
