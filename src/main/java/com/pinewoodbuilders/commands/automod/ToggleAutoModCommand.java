package com.pinewoodbuilders.commands.automod;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.administration.ListAliasesCommand;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ToggleAutoModCommand extends Command {
    public ToggleAutoModCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Toggle AutoMod Command";
    }

    @Override
    public String getDescription() {
        return "Enable or disable the filter in a server.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <true/false>` - Enable or disable the filter in the current server"
        );
    }

    @Override
    public List <String> getExampleUsage(@Nullable Message message) {
        return Collections.singletonList("`:command true` - Enable the filter in the current server");
    }

    @Override
    public List <Class <? extends Command>> getRelations() {
        return Collections.singletonList(ListAliasesCommand.class);
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("filter", "toggleautomod", "tam");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "throttle:user,2,5",
            "isGuildLeadership"
        );
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.COMMAND_CUSTOMIZATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "You didn't give any arguments.");
        }

        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "Unable to load the server settings.");
        }


        if (args[0].equals("set-channel")) {
            if (!(context.getMentionedChannels().size() > 0)) {
                context.makeError("You need to mention a channel to add it as a filter log channel.").queue();
                return false;
            }
            context.getGuildSettingsTransformer().setLocalFilterLog(context.getMentionedChannels().get(0).getIdLong());
            try {
                avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId()).update(statement -> {
                    statement.set("local_filter_log", context.getGuildSettingsTransformer().getLocalFilterLog());
                });
            } catch (SQLException throwables) {
                context.makeError("Something went wrong when setting the log channel.").queue();
            }
            context.makeSuccess("The filter log channel has successfully been set to: " + context.getMentionedChannels().get(0).getAsMention()).queue();
            return true;
        }
        switch (ComparatorUtil.getFuzzyType(args[0])) {
            case FALSE:
                setStatus(false, context);
                break;

            case TRUE:
                setStatus(true, context);
                break;

            case UNKNOWN:
                return sendErrorMessage(context, "Please enter ``true`` or ``false`` to **enable** or **disable** the filter.");
        }
        return true;
    }

    private void setStatus(boolean b, CommandMessage context) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                .useAsync(true)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("local_filter", b));
            context.getGuildSettingsTransformer().setLocalFilter(b);
            context.makeSuccess("Filter has been set to: **``" + b + "``**").queue();
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }


}
