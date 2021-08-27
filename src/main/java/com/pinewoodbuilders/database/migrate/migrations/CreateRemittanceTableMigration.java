package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateRemittanceTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Tue, Nov 1, 2020 10:37 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.REMITTANCE_DATABASE_TABLE_NAME, table -> {
            table.Increments("id");
            table.Long("pb_server_id");
            table.Long("request_message_id");
            table.Long("requester_discord_id");
            table.String("requester_discord_name");
            table.Long("requester_roblox_id");
            table.String("requester_roblox_name");
            table.String("action").nullable();
            table.String("requester_evidence");
            table.String("requester_roblox_rank").nullable();
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.REMITTANCE_DATABASE_TABLE_NAME);
    }
}
