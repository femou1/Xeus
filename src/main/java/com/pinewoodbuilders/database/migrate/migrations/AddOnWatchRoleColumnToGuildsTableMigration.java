package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.connections.MySQL;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class AddOnWatchRoleColumnToGuildsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Wed, Mar 18, 2020 4:25 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.GUILD_TABLE_NAME, "on_watch_role")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `on_watch_role` VARCHAR(32) NULL DEFAULT NULL AFTER `mute_role`;",
                Constants.GUILD_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `on_watch_role` VARCHAR(32) NULL DEFAULT NULL;",
                Constants.GUILD_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.GUILD_TABLE_NAME, "on_watch_role")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `on_watch_role`;",
            Constants.GUILD_TABLE_NAME
        ));

        return true;
    }
}
