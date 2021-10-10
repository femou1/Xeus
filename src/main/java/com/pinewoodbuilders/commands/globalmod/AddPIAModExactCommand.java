package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AddPIAModExactCommand extends Command {

    public AddPIAModExactCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "GlobalMod Exact Command";
    }

    @Override
    public String getDescription() {
        return "Add or remove EXACT words for the global filter.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList("`:command <add/remove> <word>` - Add or remove a word from the exact word list.",
                "`:command <list>` - See all the words in the exact filter.");
    }

    @Override
    public List<String> getExampleUsage(@Nullable Message message) {
        return Arrays.asList("`:command add stealing` - Add's the word ``stealing`` to the exact filter.",
                "`:command remove stealing` - Removes the word ``stealing`` from the exact filter.");
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Collections.singletonList(AddPIAModWildcardCommand.class);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("pef", "pia-ef");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList("isPinewoodGuild", "isValidMGMMember");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "You didn't give any arguments.");
        }

        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();
        if (guildTransformer == null) {
            return sendErrorMessage(context,
                    "Unable to load the local server settings. (We need this to get the MGI Connected to this guild)");
        }

        if (guildTransformer.getMainGroupId() == 0 && !guildTransformer.isOfficialSubGroup()) {
            return sendErrorMessage(context,
                    "A main group ID has not been set for this guild, or this is not an official server. So I don't know what settings to edit securely. Please set a MGI on this server, and make sure it's an officially connected server!");
        }

        GlobalSettingsTransformer transformer = context.getGlobalSettingsTransformer();
        if (transformer == null) {
            return sendErrorMessage(context,
                    "You have not set the MGI in your guild settings, so I don't know what global settings to edit.");
        }

        String words = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
        if (words.contains(" ")) {
            return sendErrorMessage(context,
                    "The EXACT words in the filter are not allowed to contain any spaces, use `!exactfilter`");
        }
        if (args[0].equalsIgnoreCase("list")) {
            return getAutoModExactList(context, transformer);
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length == 1) {
                return sendErrorMessage(context, "You didn't give any words to remove from the global filter.");
            }
            return removeAutoModExact(context, transformer, words);
        }
        if (args[0].equalsIgnoreCase("add")) {
            if (args.length == 1) {
                return sendErrorMessage(context, "You didn't give any words to add to the global filter.");
            }
            transformer.getGlobalFilterExact().add(words);
            try {
                updateGuildAutoModExact(context, transformer);

                context.makeSuccess("Successfully added: ``" + words + "``").queue();

                long mgmLogs = context.getGlobalSettingsTransformer().getMgmLogsId();
                if (mgmLogs != 0) {
                    TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                    if (tc != null) {
                        tc.sendMessageEmbeds(context.makeInfo(
                                "[The following words have been added to the **GLOBAL** exact filter by :user](:link):\n"
                                        + "```:words```")
                                .set("words", words).set("user", context.getMember().getAsMention())
                                .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
                    }
                }

                return true;
            } catch (SQLException e) {
                Xeus.getLogger().error("ERROR: ", e);
                return false;
            }
        } else {
            return sendErrorMessage(context, "Invalid argument. See ``!help exactfilter`` for the arguments.");
        }
    }

    private boolean getAutoModExactList(CommandMessage context, GlobalSettingsTransformer transformer) {
        context.makeSuccess(
                "This the list of the current filtered EXACT words: \n```" + transformer.getGlobalFilterExact() + "```")
                .queue();
        return false;
    }

    private boolean removeAutoModExact(CommandMessage context, GlobalSettingsTransformer transformer, String args) {
        if (!transformer.getGlobalFilterExact().contains(args)) {
            return sendErrorMessage(context, "This word does not exist in the list");
        }

        transformer.getGlobalFilterExact().remove(args);

        try {
            updateGuildAutoModExact(context, transformer);

            context.makeSuccess("Deleted: " + args).queue();
            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
            return false;
        }
    }

    private void updateGuildAutoModExact(CommandMessage message, GlobalSettingsTransformer transformer)
            throws SQLException {
        for (String id : Constants.guilds) {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", id)
                    .update(statement -> statement.set("global_filter_exact",
                            Xeus.gson.toJson(transformer.getGlobalFilterExact()), true));
        }
    }
}
