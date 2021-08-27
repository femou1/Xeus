package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateEvaluationsLogTableMigration implements Migration {
    @Override
    public String created_at() {
        return "Wen, Apr 15, 2020 09:56 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.EVALS_LOG_DATABASE_TABLE_NAME, table -> {
            table.Increments("id");
            table.String("roblox_username");
            table.Long("roblox_id").unsigned();
            table.String("note");
            table.String("description");
            table.String("evaluator");
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.EVALS_LOG_DATABASE_TABLE_NAME);
    }
}
