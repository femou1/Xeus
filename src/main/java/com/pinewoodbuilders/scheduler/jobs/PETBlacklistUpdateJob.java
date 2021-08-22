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

package com.pinewoodbuilders.scheduler.jobs;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.contracts.scheduler.Job;
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.requests.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PETBlacklistUpdateJob extends Job {

    private final String cacheToken = "blacklist.pet.blacklists";
    private static final Logger log = LoggerFactory.getLogger(PETBlacklistUpdateJob.class);

    public PETBlacklistUpdateJob(Xeus avaire) {
        super(avaire, 90, 90, TimeUnit.MINUTES);

        run();

    }

    @Override
    public void run() {
        handleTask(avaire -> {
            RequestFactory.makeGET("https://pb-kronos.dev/pet/blacklist")
                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosApiKey"))
                .send((Consumer<Response>) response -> {
                    log.info("PET Blacklist has been updated.");
                    List service = (List) response.toService(List.class);
                    avaire.getCache().getAdapter(CacheType.FILE).forever(cacheToken, service);
                });
        });
    }
}
