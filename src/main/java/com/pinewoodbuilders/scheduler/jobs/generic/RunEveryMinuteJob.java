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

package com.pinewoodbuilders.scheduler.jobs.generic;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Job;
import com.pinewoodbuilders.scheduler.tasks.*;

import java.util.concurrent.TimeUnit;

public class RunEveryMinuteJob extends Job {

    private final ChangeGameTask changeGameTask = new ChangeGameTask();
    private final SendRemindersTask sendRemindersTask = new SendRemindersTask();
    private final DrainMuteQueueTask drainMuteQueueTask = new DrainMuteQueueTask();
    private final DrainGlobalMuteQueueTask drainGlobalMuteQueueTask = new DrainGlobalMuteQueueTask();
    private final DrainGlobalWatchQueueTask drainGlobalWatchQueueTask = new DrainGlobalWatchQueueTask();
    private final DrainWarnQueueTask drainWarnQueueTask = new DrainWarnQueueTask();
    private final GarbageCollectorTask garbageCollectorTask = new GarbageCollectorTask();
    private final SyncBlacklistMetricsTask syncBlacklistMetricsTask = new SyncBlacklistMetricsTask();
    private final ResetRespectStatisticsTask resetRespectStatisticsTask = new ResetRespectStatisticsTask();
    private final DeleteExpiredBlacklistEntitiesTask deleteExpiredBlacklistEntitiesTask = new DeleteExpiredBlacklistEntitiesTask();
    private final UpdateWebsocketHeartbeatMetricsTask updateWebsocketHeartbeatMetricsTask = new UpdateWebsocketHeartbeatMetricsTask();
    private final SyncValidVoteRequestsWithMetricsTask syncValidVoteRequestsWithMetricsTask = new SyncValidVoteRequestsWithMetricsTask();
    private final SyncPlayerExperienceWithDatabaseTask syncPlayerExperienceWithDatabaseTask = new SyncPlayerExperienceWithDatabaseTask();
    private final SyncPlayerUpdateReferencesWithDatabaseTask syncPlayerUpdateReferencesWithDatabaseTask = new SyncPlayerUpdateReferencesWithDatabaseTask();
    private final DrainOnWatchQueueTask drainOnWatchQueueTask = new DrainOnWatchQueueTask();
    private final DrainUnbanQueueTask drainUnbanQueueTask = new DrainUnbanQueueTask();

    public RunEveryMinuteJob(Xeus avaire) {
        super(avaire, 0, 1, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        handleTask(
            changeGameTask,
            sendRemindersTask,
            drainMuteQueueTask,
            drainGlobalMuteQueueTask,
            drainWarnQueueTask,
            garbageCollectorTask,
            syncBlacklistMetricsTask,
            resetRespectStatisticsTask,
            deleteExpiredBlacklistEntitiesTask,
            updateWebsocketHeartbeatMetricsTask,
            syncValidVoteRequestsWithMetricsTask,
            syncPlayerExperienceWithDatabaseTask,
            syncPlayerUpdateReferencesWithDatabaseTask,
            drainOnWatchQueueTask,
            drainGlobalWatchQueueTask,
            drainUnbanQueueTask
        );
    }
}
