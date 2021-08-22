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
import com.google.gson.internal.LinkedTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FetchMemeTypesJob extends Job {

    private final String cacheToken = "meme.types";
    private final String apiEndpoint = "https://api.memegen.link/templates";

    public FetchMemeTypesJob(Xeus avaire) {
        super(avaire, 3, 3, TimeUnit.DAYS);

        if (!avaire.getCache().getAdapter(CacheType.FILE).has(cacheToken)) {
            run();
        }
    }

    @Override
    public void run() {
        handleTask(avaire -> {
            RequestFactory.makeGET(apiEndpoint)
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Content-Type", "application/json")
                .send((Consumer<Response>) response -> {
                    ArrayList templates = (ArrayList) response.toService(ArrayList.class);
                    HashMap<String, HashMap<String, String>> cache = new HashMap<>();

                    for (Object template : templates) {
                        LinkedTreeMap<String, Object> entry = (LinkedTreeMap<String, Object>) template;

                        HashMap<String, String> meme = new HashMap<>();
                        meme.put("name", entry.get("name").toString());
                        meme.put("lines", entry.get("lines").toString());

                        cache.put(meme.get("key"), meme);
                    }

                    avaire.getCache().getAdapter(CacheType.FILE).forever(cacheToken, cache);
                });
        });
    }
}
