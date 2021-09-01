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

public class AddVotePointsToUsersAndGuildsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Mon, May 07, 2018 10:50 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (!hasColumns(schema, Constants.BOT_VOTES_TABLE_NAME, "points", "points_total")) {
            addVoteTableColumns(schema);
        }

        if (!hasColumns(schema, Constants.GUILD_TABLE_NAME, "points")) {
            addGuildsTableColumns(schema);
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return false;
    }

    private void addVoteTableColumns(Schema schema) throws SQLException {
        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `points` INT UNSIGNED NOT NULL DEFAULT '0' AFTER `user_id`, ADD `points_total` INT UNSIGNED NOT NULL DEFAULT '0' AFTER `points`;",
                Constants.BOT_VOTES_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `points` INT NOT NULL DEFAULT '0';",
                Constants.BOT_VOTES_TABLE_NAME
            ));

            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `points_total` INT NOT NULL DEFAULT '0';",
                Constants.BOT_VOTES_TABLE_NAME
            ));
        }
    }

    private void addGuildsTableColumns(Schema schema) throws SQLException {
        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `points` INT UNSIGNED NOT NULL DEFAULT '0' AFTER `icon`;",
                Constants.GUILD_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `points` INT NOT NULL DEFAULT '0';",
                Constants.GUILD_TABLE_NAME
            ));
        }
    }

    private boolean hasColumns(Schema schema, String table, String... columns) throws SQLException {
        for (String column : columns) {
            if (!schema.hasColumn(table, column)) {
                return false;
            }
        }
        return true;
    }
}
