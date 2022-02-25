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

public class AddModerationServerToGlobalSettingsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Thu, Feb 17, 2022 10:06PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.GLOBAL_SETTINGS_TABLE, "moderation_server_id")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `moderation_server_id` VARCHAR(32) NULL DEFAULT NULL;",
                Constants.GLOBAL_SETTINGS_TABLE
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `moderation_server_id` VARCHAR(32) NULL DEFAULT NULL;",
                Constants.GLOBAL_SETTINGS_TABLE
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.GLOBAL_SETTINGS_TABLE, "moderation_server_id")) return true;

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `moderation_server_id`;",
            Constants.GLOBAL_SETTINGS_TABLE
        ));

        return true;
    }
}
