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

package com.pinewoodbuilders.contracts.shard;

import com.pinewoodbuilders.shard.ShardEntityCounter;
import net.dv8tion.jda.api.JDA;

public interface EntityGenerator {

    /**
     * Generates the shard entity value for the given shard, this is used
     * for quickly calculating the total value between shards for common
     * things by the {@link ShardEntityCounter ShardEntityCounter}.
     *
     * @param shard The shard that should be used to calculate the shard entity.
     * @return The entity value for the given shard.
     */
    long generateEntity(JDA shard);
}
