/*
 * Copyright (c) 2019.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.commands.system;

import com.google.common.base.Joiner;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.TableBuilder;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.SystemCommand;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.exceptions.DatabaseException;
import com.pinewoodbuilders.utilities.NumberUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SQLCommand extends SystemCommand {

    public SQLCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "SQL Command";
    }

    @Override
    public String getDescription() {
        return "Runs the given SQL query and returns the result.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command <query>` - Runs the given query and returns the result.");
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command SELECT name FROM guilds LIMIT 5`");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("sql");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "query");
        }

        String query = String.join(" ", args);

        try {
            switch (args[0].toUpperCase().trim()) {
                case "SELECT":
                    return runSelect(context, avaire.getDatabase().query(query));

                case "INSERT":
                    return runInsert(context, avaire.getDatabase().queryInsert(query));

                case "UPDATE":
                case "DELETE":
                    return runUpdate(context, avaire.getDatabase().queryUpdate(query));

                default:
                    context.makeWarning(
                        "Invalid or unsupported SQL action given, you just use one of the following: `SELECT`, " +
                            "`UPDATE`, `DELETE`, or `INSERT`"
                    ).queue();
            }
        } catch (SQLException | DatabaseException e) {
            context.makeError("An SQL Exception was thrown while running the query, error: " + e.getMessage()).queue();
        }

        return false;
    }

    private boolean runSelect(CommandMessage context, Collection result) {
        Set<String> keys = result.getKeys().keySet();
        List<List<String>> items = new ArrayList<>();
        for (DataRow row : result) {
            List<String> tableRow = new ArrayList<>();

            for (String key : keys) {
                String string = row.getString(key);
                tableRow.add(string == null ? "NULL" : string);
            }

            items.add(tableRow);
        }

        String tableContent = new TableBuilder()
            .setHeaders(keys)
            .setContent(items)
            .build();

        if (tableContent.trim().length() > 2000)
            tableContent = tableContent.trim().substring(0, 3900) +
                "\nData too long, " + NumberUtil.formatNicely(tableContent.length() - 3900) +
                " characters has been hidden.";

        context.getMessageChannel()
            .sendMessage("```" + tableContent + "```")
            .queue();

        return true;
    }

    private boolean runInsert(CommandMessage context, Set<Integer> ids) {
        context.makeSuccess("\uD83D\uDC4D **:records** database records has been created, record IDs:\n:ids")
            .set("records", ids.size())
            .set("ids", Joiner.on(", ").join(ids))
            .queue();

        return true;
    }

    private boolean runUpdate(CommandMessage context, int updatedRecords) {
        context.makeSuccess("\uD83D\uDC4D **:records** database records has been updated/deleted.")
            .set("records", updatedRecords)
            .queue();

        return true;
    }
}
