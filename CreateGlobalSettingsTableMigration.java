package com.pinewoodbuilders.database.migrate.migrations;

import java.sql.SQLException;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

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
            table.Boolean("global_ban").defaultValue(false);
            table.Boolean("global_kick").defaultValue(false);
            table.Boolean("global_verify").defaultValue(false);
            table.Boolean("global_anti_unban").defaultValue(false);
            table.Boolean("global_filter").defaultValue(false);
            table.LongText("global_filter_exact").nullable();
            table.LongText("global_filter_wildcard").nullable();
            table.Long("global_filter_log_channel").nullable();
            table.Boolean("global_automod").defaultValue(false).nullable();
            table.Integer("automod_mass_mention", 11).defaultValue(15);
            table.Integer("automod_emoji_spam", 11).defaultValue(15);
            table.Integer("automod_link_spam", 11).defaultValue(15);
            table.Integer("automod_message_spam", 11).defaultValue(15);
            table.Integer("automod_image_spam", 11).defaultValue(15);
            table.Integer("automod_character_spam", 11).defaultValue(15);
        });
    }    

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.GLOBAL_SETTINGS_TABLE);
    }  
}
