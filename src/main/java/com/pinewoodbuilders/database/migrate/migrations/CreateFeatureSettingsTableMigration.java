package com.pinewoodbuilders.database.migrate.migrations;

import java.sql.SQLException;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.database.schema.Schema;

public class CreateFeatureSettingsTableMigration implements Migration {
  
    @Override
    public String created_at() {
        return "Sat, Aug 28, 2021 10:48 PM";
    }

    @Override
    public boolean up(Schema schema) throws SQLException {
        return schema.createIfNotExists(Constants.FEATURE_SETTINGS_TABLE, table -> {
            table.Increments("id");
            table.Long("emoji_id").nullable();
            table.Long("on_watch_channel").nullable();
            table.Long("on_watch_role").nullable();
            table.Boolean("local_filter").defaultValue(false);
            table.Long("local_filter_log").nullable();
            table.LongText("filter_exact").nullable();
            table.LongText("filter_wildcard").nullable();
            table.Long("patrol_remittance_channel").nullable();
            table.LongText("patrol_remittance_message").nullable();
            table.Long("handbook_report_channel").nullable();
            table.Long("suggestion_channel_id").nullable();
            table.Long("suggestion_community_channel_id").nullable();
            table.Long("suggestion_approved_channel_id").nullable();
            table.Long("join_logs").nullable();
            table.Long("audit_logs_channel_id").nullable();
            table.Long("vote_validation_channel_id").nullable();
            table.Long("user_alerts_channel_id").nullable();
            table.Long("evaluation_answer_channel").nullable();
            table.LongText("eval_questions").nullable();    
        });
    }    

    @Override
    public boolean down(Schema schema) throws SQLException {
        return schema.dropIfExists(Constants.FEATURE_SETTINGS_TABLE);
    }  
}
