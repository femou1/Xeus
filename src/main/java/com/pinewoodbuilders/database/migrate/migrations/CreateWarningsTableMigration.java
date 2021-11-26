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

package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateWarningsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Wen, Nov 24, 2021 1:33 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.WARNINGS_TABLE_NAME, table -> {
            table.Long("guild_id").unsigned();
            table.Long("modlog_id").unsigned();
            table.DateTime("expires_in").nullable();
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.WARNINGS_TABLE_NAME);
    }
}
