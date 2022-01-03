package com.pinewoodbuilders.utilities;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.database.collection.Collection;
import net.dv8tion.jda.api.entities.Member;
import org.json.JSONObject;

import java.sql.SQLException;

public class ReportDatabaseRequests {

    private static Xeus avaire = Xeus.getInstance();

    public static void createReport(CommandMessage context, Member m, int reported_id, String evidence, String date, String report_reason, long message_id) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.REPORTS_DATABASE_TABLE_NAME)
                .insert(statement -> statement.set("pb_server_id", context.getGuild().getId())
                    .set("reporter_discord_id", m.getUser().getId())
                    .set("report_message_id", message_id)
                    .set("reporter_discord_name", m.getEffectiveName())
                    .set("reported_roblox_id", reported_id)
                    .set("reported_roblox_name", getRobloxUsernameFromId(reported_id))
                    .set("report_evidence", evidence)
                    .set("report_date", date)
                    .set("report_reason", report_reason, true));
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }


    }


    public static int getReportsOnRobloxId(int id) {
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.REPORTS_DATABASE_TABLE_NAME).where("reported_roblox_id", id).get();
            return c.size();
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
        return 0;
    }


    private static String getRobloxUsernameFromId(int id) {

        try {
            JSONObject json = JsonReader.readJsonFromUrl("http://api.roblox.com/users/" + id);
            return json.getString("Username");
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
