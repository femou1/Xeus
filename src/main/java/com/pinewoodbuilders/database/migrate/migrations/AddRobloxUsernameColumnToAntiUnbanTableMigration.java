package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.connections.MySQL;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class AddRobloxUsernameColumnToAntiUnbanTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Tue, Jul 7, 2021 8:46 AM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.ANTI_UNBAN_TABLE_NAME, "roblox_username")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                    "ALTER TABLE `%s` ADD `roblox_username` VARCHAR(32) NULL DEFAULT NULL AFTER `roblox_user_id`;",
                    Constants.ANTI_UNBAN_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                    "ALTER TABLE `%s` ADD `roblox_username` VARCHAR(32) NULL DEFAULT NULL;",
                    Constants.ANTI_UNBAN_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.ANTI_UNBAN_TABLE_NAME, "roblox_username")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` DROP `roblox_username`;",
                Constants.ANTI_UNBAN_TABLE_NAME
        ));

        return true;
    }
}
