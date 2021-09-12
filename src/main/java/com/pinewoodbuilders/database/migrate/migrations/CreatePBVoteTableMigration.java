package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreatePBVoteTableMigration implements Migration {

    @Override
    public String created_at() {
        return "Thu, Oct 15, 2020 11:38 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.VOTE_TABLE_NAME, table -> {
            table.Increments("id");
            table.String("vote_id");
            table.String("voted_for");
            table.Long("voter_user_id");
            table.Long("vote_message_id").nullable();
            table.LongText("description").nullable();
            table.Boolean("accepted").defaultValue(false);

            table.Timestamps();
        });
    }

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.VOTE_TABLE_NAME);
    }
}
