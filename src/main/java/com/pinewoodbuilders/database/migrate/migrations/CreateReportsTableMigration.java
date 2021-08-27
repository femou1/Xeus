package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateReportsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Tue, Nov 1, 2020 10:37 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.REPORTS_DATABASE_TABLE_NAME, table -> {
            table.Increments("id");
            table.Long("pb_server_id");
            table.Long("report_message_id");
            table.Long("reporter_discord_id");
            table.String("reporter_discord_name");
            table.Long("reported_roblox_id").unsigned();
            table.String("reported_roblox_name");
            table.String("reported_roblox_rank").nullable();
            table.String("report_punishment").nullable();
            table.String("report_evidence");
            table.String("report_reason");
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.REPORTS_DATABASE_TABLE_NAME);
    }
}
