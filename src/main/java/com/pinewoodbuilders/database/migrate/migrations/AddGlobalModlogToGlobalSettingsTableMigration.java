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

public class AddGlobalModlogToGlobalSettingsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Mon, Sep 15, 2021 8:04PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.GLOBAL_SETTINGS_TABLE, "global_modlog") && schema.hasColumn(Constants.GLOBAL_SETTINGS_TABLE, "global_modlog_case")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `global_modlog` VARCHAR(32) NULL DEFAULT NULL, ADD `global_modlog_case` INT NOT NULL DEFAULT '0';",
                Constants.GLOBAL_SETTINGS_TABLE
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `global_modlog` VARCHAR(32) NULL DEFAULT NULL;",
                Constants.GLOBAL_SETTINGS_TABLE
            ));

            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `global_modlog_case` INT NOT NULL DEFAULT '0';",
                Constants.GLOBAL_SETTINGS_TABLE
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.GLOBAL_SETTINGS_TABLE, "global_modlog") && !schema.hasColumn(Constants.GLOBAL_SETTINGS_TABLE, "global_modlog_case")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `global_modlog`, DROP `global_modlog_case`;",
            Constants.GLOBAL_SETTINGS_TABLE
        ));

        return true;
    }
}
