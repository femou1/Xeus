package com.pinewoodbuilders.database.migrate.migrations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

import java.sql.SQLException;

public class CreateGlobalSettingsTableMigration implements Migration {
  
    @Override
    public String created_at() {
        return "Sat, Sep 25, 2021 8:41 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.GLOBAL_SETTINGS_TABLE, table -> {
            table.String("main_group_name");
            table.Long("main_group_id");

            table.Boolean("global_filter").defaultValue(false);
            table.Long("global_filter_log_channel").nullable();
            table.LongText("global_filter_exact").nullable();
            table.LongText("global_filter_wildcard").nullable();

            table.Long("appeals_discord_id").nullable();
            table.Long("mgm_logs").nullable();
        });
    }    

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.GLOBAL_SETTINGS_TABLE);
    }  
}
