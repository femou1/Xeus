package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateRewardTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Mon, Jan 17, 2022 8:24 AM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.REWARD_REQUESTS_TABLE_NAME, table -> {
            table.Long("message_id"); // Message ID
            table.Long("server_id"); // Guild
            table.Long("discord_id"); // Requester
            table.Long("roblox_id"); // Reciever
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.REWARD_REQUESTS_TABLE_NAME);
    }
}
