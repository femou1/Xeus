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

package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.connections.MySQL;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class AddModlogToGuildsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Mon, Feb 12, 2018 3:20 AM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.GUILD_TABLE_NAME, "modlog") && schema.hasColumn(Constants.GUILD_TABLE_NAME, "modlog_case")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `modlog` VARCHAR(32) NULL DEFAULT NULL AFTER `autorole`, ADD `modlog_case` INT NOT NULL DEFAULT '0' AFTER `modlog`;",
                Constants.GUILD_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `modlog` VARCHAR(32) NULL DEFAULT NULL;",
                Constants.GUILD_TABLE_NAME
            ));

            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `modlog_case` INT NOT NULL DEFAULT '0';",
                Constants.GUILD_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.GUILD_TABLE_NAME, "modlog") && !schema.hasColumn(Constants.GUILD_TABLE_NAME, "modlog_case")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `modlog`, DROP `modlog_case`;",
            Constants.GUILD_TABLE_NAME
        ));

        return true;
    }
}
