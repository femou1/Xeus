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
import com.pinewoodbuilders.scheduler.tasks.ApplicationShutdownTask;
import com.pinewoodbuilders.scheduler.tasks.DrainReactionRoleQueueTask;
import com.pinewoodbuilders.scheduler.tasks.DrainVoteQueueTask;
import com.pinewoodbuilders.scheduler.tasks.DrainWeatherQueueTask;

import java.util.concurrent.TimeUnit;

public class RunEverySecondJob extends Job {

    private final DrainVoteQueueTask emptyVoteQueueTask = new DrainVoteQueueTask();
    private final ApplicationShutdownTask shutdownTask = new ApplicationShutdownTask();
    private final DrainWeatherQueueTask drainWeatherQueueTask = new DrainWeatherQueueTask();
    private final DrainReactionRoleQueueTask reactionRoleQueueTask = new DrainReactionRoleQueueTask();

    public RunEverySecondJob(Xeus avaire) {
        super(avaire, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        handleTask(emptyVoteQueueTask, shutdownTask, drainWeatherQueueTask, reactionRoleQueueTask);
    }
}
