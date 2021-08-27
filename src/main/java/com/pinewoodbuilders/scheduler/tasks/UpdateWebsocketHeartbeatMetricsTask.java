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
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.metrics.Metrics;
import net.dv8tion.jda.api.JDA;

public class UpdateWebsocketHeartbeatMetricsTask implements Task {

    @Override
    public void handle(Xeus avaire) {
        if (!avaire.areWeReadyYet()) {
            return;
        }

        for (JDA shard : avaire.getShardManager().getShards()) {
            Metrics.websocketHeartbeat.labels("Shard " + shard.getShardInfo().getShardId())
                .set(shard.getGatewayPing());
        }
    }
}
