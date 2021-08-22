package com.pinewoodbuilders.scheduler.jobs.generic;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Job;
import com.pinewoodbuilders.scheduler.tasks.CheckUserOnlineStatus;

import java.util.concurrent.TimeUnit;

public class RunEveryFiveMinuteJob extends Job {

    private final CheckUserOnlineStatus userOnlineStatus = new CheckUserOnlineStatus();

    public RunEveryFiveMinuteJob(Xeus avaire) {
        super(avaire, 0, 5, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        handleTask(
            userOnlineStatus
        );
    }
}
