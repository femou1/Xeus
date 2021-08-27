package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.connections.MySQL;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class AddMainAccountColumnToVerificationsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Sun, Jul 11, 2021 5:56 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.VERIFICATION_DATABASE_TABLE_NAME, "main")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `main` BOOLEAN NULL DEFAULT NULL AFTER `robloxId`;",
                Constants.VERIFICATION_DATABASE_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `main` BOOLEAN NULL DEFAULT NULL;",
                Constants.VERIFICATION_DATABASE_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.VERIFICATION_DATABASE_TABLE_NAME, "main")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `main`;",
            Constants.VERIFICATION_DATABASE_TABLE_NAME
        ));

        return true;
    }
}
