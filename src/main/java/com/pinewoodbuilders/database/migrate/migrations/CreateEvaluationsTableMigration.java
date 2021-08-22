package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateEvaluationsTableMigration implements Migration {
    @Override
    public String created_at() {
        return "Tue, Apr 14, 2020 09:56 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.EVALS_DATABASE_TABLE_NAME, table -> {
            table.Increments("id");
            table.String("roblox_username");
            table.Long("roblox_id").unsigned();
            table.Boolean("passed_quiz").defaultValue(false).nullable();
            table.Boolean("passed_patrol").defaultValue(false).nullable();
            table.String("evaluator").nullable();
            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.EVALS_DATABASE_TABLE_NAME);
    }
}
