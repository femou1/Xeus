package com.pinewoodbuilders.commands.automod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AddAutoModExactCommand extends Command {

    public AddAutoModExactCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "AutoMod Exact Command";
    }

    @Override
    public String getDescription() {
        return "Add or remove EXACT words for the filter.";
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
        return Collections.singletonList(ToggleAutoModCommand.class);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("exactfilter", "ef");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList("isPinewoodGuild", "throttle:user,2,5", "isGuildLeadership");
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

        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "Unable to load the server settings.");
        }

        if (!transformer.getLocalFilter()) {
            return sendErrorMessage(context, "The filter is disabled, enable the filter with `!toggleautomod`");
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
            return removeAutoModExact(context, transformer, words);
        }
        if (args[0].equalsIgnoreCase("add")) {

            transformer.getBadWordsExact().add(words);
            try {
                updateGuildAutoModExact(context, transformer);

                context.makeSuccess("Successfully added: ``" + words + "``").queue();

                long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
                if (mgmLogs != 0) {
                    TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                    if (tc != null) {
                        tc.sendMessageEmbeds(context.makeInfo(
                                "[The following words have been added to the **LOCAL** exact filter by :user in ``:guild``](:link):\n"
                                        + "```:words```")
                                .set("guild", context.getGuild().getName()).setColor(new Color(255, 128, 0))
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

    private boolean getAutoModExactList(CommandMessage context, GuildSettingsTransformer transformer) {
        context.makeSuccess(
                "This the list of the current filtered EXACT words: \n```" + transformer.getBadWordsExact() + "```")
                .queue();
        return false;
    }

    private boolean removeAutoModExact(CommandMessage context, GuildSettingsTransformer transformer, String args) {
        if (!transformer.getBadWordsExact().contains(args)) {
            return sendErrorMessage(context, "This word does not exist in the list");
        }

        transformer.getBadWordsExact().remove(args);

        try {
            updateGuildAutoModExact(context, transformer);

            context.makeSuccess("Deleted: " + args).queue();
            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
            return false;
        }
    }

    private void updateGuildAutoModExact(CommandMessage message, GuildSettingsTransformer transformer)
            throws SQLException {
        avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", message.getGuild().getId())
                .update(statement -> statement.set("filter_exact", Xeus.gson.toJson(transformer.getBadWordsExact()),
                        true));
    }
}
