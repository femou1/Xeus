package com.pinewoodbuilders.database.migrate.migrations;

import java.sql.SQLException;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

public class CreateModerationTableMigration implements Migration {
  
    @Override
    public String created_at() {
        return "Sat, Aug 28, 2021 10:20 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.GROUP_MODERATORS_TABLE, table -> {
            table.Long("discord_id");
            table.Long("roblox_id"); // If either the Discord ID or Roblox ID doesn't match. You do not get Moderator permissions for said group
            table.Long("main_group_id").nullable();
            table.Boolean("is_global_lead").defaultValue(false);
            table.Boolean("is_global_admin").defaultValue(false);
        });
    }    

    @Override   
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.GROUP_MODERATORS_TABLE);
    }  
}
