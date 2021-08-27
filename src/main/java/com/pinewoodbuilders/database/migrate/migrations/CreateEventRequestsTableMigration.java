package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateEventRequestsTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Sat, Apr 10, 2021 11:43 AM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.EVENT_SCHEDULE_REQUESTS_TABLE_NAME, table -> {
            table.Increments("id");
            table.Long("guild_id");
            table.Long("request_message_id");
            table.Long("requester_discord_id");
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.EVENT_SCHEDULE_REQUESTS_TABLE_NAME);
    }
}
