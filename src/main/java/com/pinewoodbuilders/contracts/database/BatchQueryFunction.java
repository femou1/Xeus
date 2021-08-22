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

package com.pinewoodbuilders.contracts.database;

import com.pinewoodbuilders.database.DatabaseManager;

import java.sql.SQLException;

@FunctionalInterface
public interface BatchQueryFunction<PreparedStatement> {

    /**
     * Runs the batch query function with the prepared statement generated through the query given to
     * the {@link DatabaseManager#queryBatch(String, BatchQueryFunction) database queryBatch method}.
     *
     * @param statement The prepared statement that should be used for the batch request.
     * @throws SQLException If an error occured
     */
    void run(PreparedStatement statement) throws SQLException;
}
