package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.connections.MySQL;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class AddWarningEvidenceToReportsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Mon, Jan 11, 2021 8:08 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        if (schema.hasColumn(Constants.REPORTS_DATABASE_TABLE_NAME, "report_evidence_warning")) {
            return true;
        }

        if (schema.getDbm().getConnection() instanceof MySQL) {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `report_evidence_warning` TEXT NULL DEFAULT NULL AFTER `report_evidence`;",
                Constants.REPORTS_DATABASE_TABLE_NAME
            ));
        } else {
            schema.getDbm().queryUpdate(String.format(
                "ALTER TABLE `%s` ADD `report_evidence_warning` TEXT;",
                Constants.REPORTS_DATABASE_TABLE_NAME
            ));
        }

        return true;
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        if (!schema.hasColumn(Constants.REPORTS_DATABASE_TABLE_NAME, "report_evidence_warning")) {
            return true;
        }

        schema.getDbm().queryUpdate(String.format(
            "ALTER TABLE `%s` DROP `report_evidence_warning`;",
            Constants.REPORTS_DATABASE_TABLE_NAME
        ));

        return true;
    }
}
