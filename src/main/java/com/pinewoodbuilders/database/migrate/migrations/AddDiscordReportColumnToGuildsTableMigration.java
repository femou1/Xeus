package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.connections.MySQL;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class AddDiscordReportColumnToGuildsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Sun, August 23, 2020 11:20 AM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.GUILD_TABLE_NAME, "report_discord")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `report_discord` TEXT NULL DEFAULT NULL AFTER `modlog_case`;",
                Constants.GUILD_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `report_discord` TEXT NULL DEFAULT NULL;",
                Constants.GUILD_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.GUILD_TABLE_NAME, "report_discord")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `report_discord`;",
            Constants.GUILD_TABLE_NAME
        ));

        return true;
    }
}
