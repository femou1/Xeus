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

package com.pinewoodbuilders.scheduler.tasks;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.blacklist.bot.Ratelimit;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.cache.adapters.MemoryAdapter;
import com.pinewoodbuilders.commands.administration.MuteRoleCommand;
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.handlers.adapter.JDAStateEventAdapter;
import com.pinewoodbuilders.handlers.adapter.MessageEventAdapter;

public class GarbageCollectorTask implements Task {

    @Override
    public void handle(Xeus avaire) {
        // Clears the user cache set for DM info messages, this will reset
        // the list, allowing users to get the DM info message again.
        MessageEventAdapter.hasReceivedInfoMessageInTheLastMinute.clear();

        // Remove cache entries from the memory cache adapter
        // if the keys are still stored by has expired.
        MemoryAdapter adapter = (MemoryAdapter) avaire.getCache().getAdapter(CacheType.MEMORY);
        adapter.getCacheKeys().removeIf(key -> !adapter.has(key));

        // Cleans up caches that are not hit very often, so
        // instead of just keeping the entities in the
        // cache, we can clean them up here.
        cleanupCache();
    }

    /**
     * Goes through some of the less used caches and
     * cleans up any entities that have expired.
     */
    private void cleanupCache() {
        // blacklist-ratelimit
        synchronized (Ratelimit.cache) {
            Ratelimit.cache.cleanUp();
        }

        // autorole
        synchronized (JDAStateEventAdapter.cache) {
            JDAStateEventAdapter.cache.cleanUp();
        }

        // muterole
        synchronized (MuteRoleCommand.cache) {
            MuteRoleCommand.cache.cleanUp();
        }
    }
}
