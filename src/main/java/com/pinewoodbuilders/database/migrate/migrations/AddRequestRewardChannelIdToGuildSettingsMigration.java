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

public class AddRequestRewardChannelIdToGuildSettingsMigration implements Migration {

    @Override
    public String created_at() {
        return "Sun, Jan 2, 2022 9:28 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.GUILD_SETTINGS_TABLE, "reward_request_channel_id")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `reward_request_channel_id` LONG DEFAULT 0 AFTER `link_filter_log`;",
                Constants.GUILD_SETTINGS_TABLE
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `reward_request_channel_id` LONG DEFAULT 0;",
                Constants.GUILD_SETTINGS_TABLE
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.GUILD_SETTINGS_TABLE, "reward_request_channel_id")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `reward_request_channel_id`;",
            Constants.GUILD_SETTINGS_TABLE
        ));

        return true;
    }
}
