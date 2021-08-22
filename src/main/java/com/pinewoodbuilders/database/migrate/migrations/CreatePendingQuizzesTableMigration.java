package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreatePendingQuizzesTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Fri, Jul 27, 2021 9:15 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.PENDING_QUIZ_TABLE_NAME, table -> {
            table.Long("roblox_id");
            table.Long("server_id");
            table.Long("message_id");
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.PENDING_QUIZ_TABLE_NAME);
    }
}
