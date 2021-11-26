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

package com.pinewoodbuilders.database.seeder.seeders;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.database.seeder.Seeder;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogType;

import java.sql.SQLException;

public class MGMLogTypesTableSeeder extends Seeder {

    public MGMLogTypesTableSeeder(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String table() {
        return Constants.MGM_LOG_TYPES_TABLE_NAME;
    }

    @Override
    public void run() throws SQLException {
        for (GlobalModlogType type : GlobalModlogType.values()) {
            if (!tableHasValue("id", type.getId())) {
                createRecord(type.getId(), type.getName(null));
            }
        }
    }

    private void createRecord(int id, String name) throws SQLException {
        createQuery().insert(statement -> {
            statement.set("id", id);
            statement.set("name", name);
        });
    }
}
