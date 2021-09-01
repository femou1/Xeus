package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateXeusVotesTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Thu, Oct 15, 2020 10:38 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.VOTES_TABLE_NAME, table -> {
            table.Increments("id");
            table.String("question");
            table.Integer("total_votes");
            table.String("vote_id");
            table.Long("guild_id");
            table.Boolean("active").defaultValue(true);
            //table.DateTime("end_date");
            table.Timestamps();
        });

    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.VOTES_TABLE_NAME);
    }
}
