package com.avairebot.commands.roblox.verification;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.collection.DataRow;
import com.avairebot.database.query.QueryBuilder;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class SelectAccountCommand extends Command {

    public SelectAccountCommand(AvaIre avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Select Account Command";
    }

    @Override
    public String getDescription() {
        return "Select the account you want to use for verification.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
                "`:command` - Start the account selector."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
                "`:command` - Meep Meep"
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
                "isOfficialPinewoodGuild"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("select-account", "sa");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        try {
            QueryBuilder accounts = avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME)
                    .where("id", context.getAuthor().getId());

            if (args.length == 0) {
                return startAccountSelector(context, accounts);
            }

            switch (args[0]) {
                case "view":
                default:
                    return runAccountCheck(context, accounts);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

       /* VerificationEntity verificationEntity;

        if (verificationEntity != null) {
            return startAccountSelector(context, verificationEntity);
        } else {
            context.makeError("You're not verified in the database. Please verify with `!verify`").queue();
        }*/
        return false;
    }

    private boolean runAccountCheck(CommandMessage context, QueryBuilder accounts) throws SQLException {
        Collection b = avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME)
                .where("id", context.getAuthor().getId()).get();

        if (b.size() == 0) {
            context.makeInfo("Unable to find any roblox account linked to your discord.").queue();
            return false;
        } else {
            for (DataRow dataRow : b) {
                context.makeInfo(dataRow.getLong("id") + " - " + dataRow.getString("robloxId") + " - " + (dataRow.getBoolean("main_roblox_account") ? "✅" : "❌") + " - " + (dataRow.getBoolean("main_discord_account") ? "✅" : "❌")).queue();
            }
        }
        return false;
    }

    private boolean startAccountSelector(CommandMessage context, QueryBuilder verificationEntity) {
        return false;
    }
}
