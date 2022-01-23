package com.pinewoodbuilders.commands.evaluations;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.collection.Collection;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.pinewoodbuilders.utilities.JsonReader.readJsonFromUrl;

public class LogCommand extends Command {

    public LogCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Eval Log Command";
    }

    @Override
    public String getDescription() {
        return "Commands to log in the evaluations.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <roblox username>` - Get the logs.",
            "`:command <roblox username> good/bad/neutral reason` - Fail/Succeed someone for a Quiz/Patrol"
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command superstefano4` - Get the eval status of **superstefano4**.",
            "`:command Cdsi passed quiz` - Succeed **Csdi** for a Quiz"
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("log", "loguser");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.EVALUATIONS
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "isGuildHROrHigher",
            "throttle:user,1,1"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!(context.guild.getId().equalsIgnoreCase("699379074505637908") || context.guild.getId().equalsIgnoreCase("438134543837560832"))) {
            context.makeError("YOU CANNOT EXECUTE THIS COMMAND HERE").queue();
            return true;
        }
        if (args.length < 1) {
            context.makeError("Invalid usage of command. Please add the required arguments. (Begin with the roblox name)").queue();
            return false;
        }
        if (args[0].equalsIgnoreCase("delete-all")) {
            try {
                Collection s = avaire.getDatabase().newQueryBuilder(Constants.EVALS_LOG_DATABASE_TABLE_NAME).get();
                if (s.size() < 1) {
                    context.makeError("The log database is already empty").queue();
                    return false;
                }
                avaire.getDatabase().getConnection().truncate(Constants.EVALS_LOG_DATABASE_TABLE_NAME);
                context.makeSuccess("Truncated the complete eval log database (Deleted every log in the database)").queue();
                return true;
            } catch (SQLException e) {
                Xeus.getLogger().error("ERROR: ", e);
            }
        }
        if (!isValidRobloxUser(args[0])) {
            context.makeError("This user is not a valid robloxian.").queue();
            return false;
        }
        try {
            Collection log_collection = avaire.getDatabase().newQueryBuilder(Constants.EVALS_LOG_DATABASE_TABLE_NAME).where("roblox_id", getRobloxId(args[0])).get();

            if (args.length == 1) {
                if (log_collection.size() < 1) {
                    context.makeEmbeddedMessage(new Color(21, 34, 255)).setDescription("There aren't any logs about this user.\n" +
                        "Log him/her with:\n" +
                        " ``!log " + args[0] + " good/neutral/bad description``").queue();
                    return true;
                }

                if (log_collection.size() > 0) {
                    context.channel.sendMessage("**Green bar**: Good\n" +
                        "**Orange bar**: Avarage\n" +
                        "**Red bar**: Neutral\n\n" +
                        "I found the following logs on that user:").queue();
                    log_collection.forEach(row -> {
                        String evaluator = row.getString("evaluator") != null ? row.getString("evaluator") : "Unkown Evaluator";
                        String profile_picture = "https://www.roblox.com/Thumbs/Avatar.ashx?x=256&y=256&Format=Png&username=" + args[0];
                        String notes = row.getString("note");
                        String description = row.getString("description", null);
                        PlaceholderMessage eb = context.makeEmbeddedMessage()
                            .setThumbnail(profile_picture)
                            .setFooter("Evaluator: " + evaluator)
                            .setTitle("Logs of: " + args[0])
                            .setDescription("**Notes**: ``" + notes + "``\n" +
                                "**Description**: \n" +
                                description);
                        eb.setColor(returnColorFromNote(notes)).queue();
                    });
                    return true;
                }
                return true;
            }
            if (args.length == 2 && isValidNote(args[1])) {
                context.makeError("What do you want to log this user for?\n" +
                    "**Log a " + args[1] + " user**:\n ``!log " + args[0] + " log " + args[1] + " Information about the log``").queue();
                return true;
            }
            if (args.length > 2 && isValidNote(args[1])) {
                String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    .replaceAll("\\\\n", "\n");
                avaire.getDatabase().newQueryBuilder(Constants.EVALS_LOG_DATABASE_TABLE_NAME)
                    .insert(statement -> statement
                        .set("roblox_username", args[0])
                        .set("roblox_id", getRobloxId(args[0]))
                        .set("note", args[1])
                        .set("description", description, true)
                        .set("evaluator", context.getMember().getEffectiveName()));
                context.makeEmbeddedMessage().setColor(returnColorFromNote(args[1])).setDescription("The user ``" + args[0] + "`` has been loggged with:\n" +
                    "``" + description + "``").queue();

            } else {
                context.makeError("You're using an invalid note. Please use good, neutral or bad").queue();
            }


        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
        return false;

    }

    private boolean isValidNote(String arg) {
        return arg.equalsIgnoreCase("good") || arg.equalsIgnoreCase("bad") || arg.equalsIgnoreCase("neutral");
    }

    private Color returnColorFromNote(String notes) {
        if (notes.equalsIgnoreCase("good")) return new Color(0, 255, 0);
        if (notes.equalsIgnoreCase("neutral")) return new Color(255, 127, 0);
        if (notes.equalsIgnoreCase("bad")) return new Color(255, 0, 0);
        else return new Color(0, 0, 0);
    }

    private static String getRobloxUsernameFromId(int id) {
        try {
            JSONObject json = readJsonFromUrl("http://api.roblox.com/users/" + id);
            return json.getString("Username");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValidRobloxUser(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un) != 0;
        } catch (Exception e) {
            return false;
        }
    }


}
