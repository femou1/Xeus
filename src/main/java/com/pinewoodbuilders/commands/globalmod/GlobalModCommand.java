package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GlobalModCommand extends Command {

    public GlobalModCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Global Mod Command";
    }

    @Override
    public String getDescription() {
        return "Manage the global moderation settings (Filter for emotes, mentions etc).";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Manage the moderation across all PB Guilds.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("global-mod");
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

    public final ArrayList<String> guilds = new ArrayList<String>() {
        {
            add("495673170565791754"); // Aerospace
            add("438134543837560832"); // PBST
            add("791168471093870622"); // Kronos Dev
            add("371062894315569173"); // Official PB Server
            add("514595433176236078"); // PBQA
            add("436670173777362944"); // PET
            add("505828893576527892"); // MMFA
            add("498476405160673286"); // PBM
            add("572104809973415943"); // TMS
            add("758057400635883580"); // PBOP
            add("669672893730258964"); // PB Dev
        }
    };

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();

        if (transformer == null) {
            context.makeError("Server settings cannot be loaded. Please run this command in a pinewood server.")
                    .queue();
            return false;
        }

        if (transformer.getMainGroupId() == 0) {
            context.makeError(
                    "This discord server has no MGI (Main Group ID), please let a MGL+ set this for this server.")
                    .queue();
            return false;
        }

        if (args.length == 0) {
            context.makeInfo("Please select what setting you'd like to modify (0 = Disabled)\n"
                    + " - ``mass-mention`` - "
                    + (transformer.getMassMention() != 0 ? transformer.getMassMention() : "**Empty/Disabled**") + ")"
                    + "\n" + " - ``emoji-spam`` - "
                    + (transformer.getEmojiSpam() != 0 ? transformer.getEmojiSpam() : "**Empty/Disabled**") + ")" + "\n"
                    + " - ``link-spam`` - "
                    + (transformer.getLinkSpam() != 0 ? transformer.getLinkSpam() : "**Empty/Disabled**") + ")" + "\n"
                    + " - ``message-spam`` - "
                    + (transformer.getMessageSpam() != 0 ? transformer.getMessageSpam() : "**Empty/Disabled**") + ")"
                    + "\n" + " - ``image-spam`` - "
                    + (transformer.getImageSpam() != 0 ? transformer.getImageSpam() : "**Empty/Disabled**") + ")" + "\n"
                    + " - ``character-spam`` - "
                    + (transformer.getCharacterSpam() != 0 ? transformer.getCharacterSpam() : "**Empty/Disabled**")
                    + ")" + "\n" + " - ``young-warning-channel`` (local) ("
                    + (context.getGuildSettingsTransformer().getUserAlertsChannelId() != 0
                            ? context.getGuildSettingsTransformer().getUserAlertsChannelId()
                            : "**Empty/Disabled**")
                    + ")").queue();
            return false;
        }

        switch (args[0]) {
            case "mass-mention":
                return runMentionUpdateCommand(context, args, transformer);
            case "emoji-spam":
                return runEmojiSpamUpdateCommand(context, args, transformer);
            case "link-spam":
                return runLinkSpamUpdateCommand(context, args, transformer);
            case "message-spam":
                return runMessageSpamUpdateCommand(context, args, transformer);
            case "image-spam":
                return runImageSpamUpdateCommand(context, args, transformer);
            case "character-spam":
                return runCharacterSpamUpdateCommand(context, args, transformer);
            case "young-warning-channel":
                return runYoungWarningChannelUpdateCommand(context, args, context.getGuildSettingsTransformer());
        }

        return true;
    }

    private boolean runYoungWarningChannelUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        TextChannel c = context.getGuild().getTextChannelById(args[1]);

        if (NumberUtil.isNumeric(args[1]) && c != null) {
            context.makeInfo("Updated young member warning channel to " + c.getName()).queue();
            transformer.setUserAlertsChannelId(Long.parseLong(args[1]));
            return updateLocalRecordInDatabase(context, "user_alerts_channel_id", transformer.getUserAlertsChannelId());
        } else {
            context.makeError("Please enter a valid channel ID.").queue();
            return false;
        }
    }

    private boolean updateLocalRecordInDatabase(CommandMessage context, String member_to_young_channel_id,
            long memberToYoungChannelId) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("id", context.getGuild().getId()).update(p -> {
                        p.set(member_to_young_channel_id, memberToYoungChannelId);
                    });
            context.makeSuccess("Channel was set!").queue();
        } catch (SQLException exception) {
            Xeus.getLogger().error("ERROR: ", exception);
            context.makeError("Something went wrong...");
            return false;
        }
        context.makeSuccess("Done!").queue();
        return true;
    }

    private boolean runCharacterSpamUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update character spam to " + args[1]).queue();
            transformer.setCharacterSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_character_spam", transformer.getCharacterSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }

    }

    private boolean runImageSpamUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        context.makeInfo("Update image spam to " + args[1]).queue();
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update link spam to " + args[1]).queue();
            transformer.setImageSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_image_spam", transformer.getImageSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runMessageSpamUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update message spam to " + args[1]).queue();
            transformer.setMessageSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_message_spam", transformer.getMessageSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runLinkSpamUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update link spam to " + args[1]).queue();
            transformer.setLinkSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_link_spam", transformer.getLinkSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runEmojiSpamUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update emoji spam to " + args[1]).queue();
            transformer.setEmojiSpam(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_emoji_spam", transformer.getEmojiSpam());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean runMentionUpdateCommand(CommandMessage context, String[] args,
            GuildSettingsTransformer transformer) {
        if (NumberUtil.isNumeric(args[1])) {
            context.makeInfo("Update mention spam to " + args[1]).queue();
            transformer.setMassMention(Integer.parseInt(args[1]));
            return updateRecordInDatabase(context, "automod_mass_mention", transformer.getMassMention());
        } else {
            context.makeError("Please enter a number.").queue();
            return false;
        }
    }

    private boolean updateRecordInDatabase(CommandMessage context, String table, int setTo) {
        try {

            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("main_group_id", context.getGuildSettingsTransformer().getMainGroupId())
                    .update(statement -> statement.set(table, setTo));

            context.makeSuccess("Updated!").queue();

            TextChannel tc = avaire.getShardManager().getTextChannelById(Constants.PIA_LOG_CHANNEL);
            if (tc != null) {
                tc.sendMessageEmbeds(
                        context.makeInfo("[``:tableSetting`` was changed to ``:value`` by :mention](:link)")
                                .set("tableSetting", table).set("value", setTo)
                                .set("mention", context.getMember().getAsMention())
                                .set("link", context.getMessage().getJumpUrl()).buildEmbed())
                        .queue();
            }

            return true;
        } catch (SQLException throwables) {
            Xeus.getLogger().error("ERROR: ", throwables);
            context.makeError("Database error!").queue();
            return false;
        }
    }

}
