package com.pinewoodbuilders.commands.utility;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.blacklist.features.FeatureScope;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EventRemittanceCommand extends Command {

    public static final Cache <Long, Guild> defaultCache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build();

    public static final Cache <Long, Guild> petcache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterWrite(48, TimeUnit.HOURS)
        .build();

    public EventRemittanceCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Event Remittance Command";
    }

    @Override
    public String getDescription() {
        return "Run this command to request points for your division (If they allow so).";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Start the command, questions for evidence will be asked. Linked to the roblox account you're currently linked with on roblox."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command` - Start the command, questions for evidence will be asked.",
            "`:command set-channel #channel/722805575053738034` - Set the channel mentioned or the ID to the id"
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("event-remittance", "er", "pr");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.MISCELLANEOUS
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "throttle:guild,1,30"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("Something went wrong loading the guild settings. Please try again later.").queue();
            return false;
        }
        if (args.length > 0) {
            if (XeusPermissionUtil.getPermissionLevel(context).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
                switch (args[0].toLowerCase()) {
                    case "sc":
                    case "set-channel": {
                        return runSetRemittanceChannel(context, args);
                    }
                    case "clear":
                    case "reset": {
                        return runClearAllChannelsFromDatabase(context);
                    }
                    default: {
                        return sendErrorMessage(context, "Invalid argument given.");
                    }
                }
            }
        }

        return startRemittanceWaiter(context, args);
    }

    private boolean startRemittanceWaiter(CommandMessage context, String[] args) {
        if (checkAccountAge(context)) {
            context.makeError("Sorry, but only discord accounts that are older then 3 days are allowed to make remittance requests.").queue();
            return false;
        }

        context.makeInfo("<a:loading:742658561414266890> Loading servers that are using event remittance...").queue(l -> {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).orderBy("patrol_remittance_channel");
            try {
                StringBuilder sb = new StringBuilder();
                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("patrol_remittance_channel") != null) {
                        Guild g = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                        RichCustomEmoji e = avaire.getShardManager().getEmojiById(dataRow.getString("emoji_id"));

                        if (g != null && e != null) {
                            sb.append("``").append(g.getName()).append("`` - ").append(e.getAsMention()).append("\n");
                            l.addReaction(e).queue();
                        } else {
                            context.makeError("Either the guild or the emote can't be found in the database, please check with the developer.").queue();
                        }
                    }

                });
                l.addReaction(Emoji.fromFormatted("❌")).queue();
                l.editMessageEmbeds(context.makeInfo("Welcome to the event remittance system. With this feature, you can record your patrolling/raiding for groups that have this enabled! (Please check the rules regarding what events they allow remittance for)\n\n" + sb.toString()).buildEmbed()).queue(
                    message -> {
                        avaire.getWaiter().waitForEvent(MessageReactionAddEvent.class, event -> {
                                return event.getMember().equals(context.member) && event.getMessageId().equalsIgnoreCase(message.getId());
                            }, react -> {
                                try {
                                    if (react.getReaction().getEmoji().getName().equalsIgnoreCase("❌")) {
                                        message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).queue();
                                        message.clearReactions().queue();
                                        return;
                                    }
                                    DataRow d = qb.where("emoji_id", react.getReaction().getEmoji().asCustom().getId()).get().get(0);

                                    TextChannel c = avaire.getShardManager().getTextChannelById(d.getString("patrol_remittance_channel"));
                                    if (c != null) {
                                        if (avaire.getFeatureBlacklist().isBlacklisted(context.getAuthor(), c.getGuild().getIdLong(), FeatureScope.PATROL_REMITTANCE)) {
                                            message.editMessageEmbeds(context.makeError("You have been blacklisted from requesting a remittance for this guild. Please ask a **Level 4** (Or higher) member to remove you from the ``" + c.getGuild().getName() + "`` remittance blacklist.").buildEmbed()).queue();
                                            return;
                                        }
                                        message.editMessageEmbeds(context.makeInfo(d.getString("patrol_remittance_message", "A remittance message for ``:guild`` could not be found. Ask the HR's of ``:guild`` to set one.\n" +
                                            "If you'd like to request remittance, please enter evidence of this in right now." + "``` ```\n\nPlease enter a **LINK** to evidence. " +
                                            "\n**Remember, you may only post once per 24 hours. The video may *only be 2 hours* and has to have a *minimum of 30 minutes* in duration**\n\n" +
                                            "**We're accepting**:\n" +
                                            "- [YouTube Links](https://www.youtube.com/upload)\n" +
                                            "- [Streamable](https://streamable.com)\n" +
                                            "If you want a link/video service added, please ask ``Stefano#7366``")).set("guild", d.getString("name")).set(":user", context.member.getEffectiveName()).buildEmbed()).queue(
                                            nameMessage -> {
                                                avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, m -> m.getMember().equals(context.member) && message.getChannel().equals(l.getChannel()) && checkEvidenceAcceptance(context, m),
                                                    content -> {
                                                        goToStep2(context, message, content, d, c);
                                                    },
                                                    90, TimeUnit.SECONDS,
                                                    () -> message.editMessage("You took to long to respond, please restart the remittace system!").queue());
                                            }
                                        );
                                        message.clearReactions().queue();
                                    } else {
                                        context.makeError("The guild doesn't have a (valid) channel for remittance").queue();
                                    }

                                } catch (SQLException throwables) {
                                    context.makeError("Something went wrong while checking the database, please check with the developer for any errors.").queue();
                                }
                            },
                            5, TimeUnit.MINUTES,
                            () -> {
                                message.editMessage("You took to long to respond, please restart the report system!").queue();
                            });
                    }
                );

            } catch (SQLException throwables) {
                context.makeError("Something went wrong while checking the database, please check with the developer for any errors.").queue();
            }
        });
        return true;
    }


    private boolean checkIfBlacklisted(Long requestedId, TextChannel c) {
        if (c.getGuild().getId().equalsIgnoreCase("438134543837560832")) {
            return avaire.getBlacklistManager().getPBSTBlacklist().contains(requestedId);
        } else if (c.getGuild().getId().equalsIgnoreCase("572104809973415943")) {
            return avaire.getBlacklistManager().getTMSBlacklist().contains(requestedId);
        } else if (c.getGuild().getId().equalsIgnoreCase("436670173777362944")) {
            return avaire.getBlacklistManager().getPETBlacklist().contains(requestedId);
        } else {
            return false;
        }
    }

    private void goToStep2(CommandMessage context, Message message, MessageReceivedEvent content, DataRow d, TextChannel c) {
        {
            List <Message> messagesToRemove = new ArrayList <>();
            messagesToRemove.add(content.getMessage());
            if (content.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(context.getMember().getId());
            if (ve == null) {
                context.makeError("Sorry, but you are not linked to our databases in any way. Please run `!verify` to get your nickname updated.").queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            Long requestedId = ve.getRobloxId();
            boolean isBlacklisted = checkIfBlacklisted(requestedId, c);
            if (isBlacklisted) {
                message.editMessageEmbeds(context.makeWarning("You're blacklisted in ``" + c.getGuild().getName() + "``, for this reason you will not be allowed to request a remittance.").buildEmbed()).queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            boolean hasNotExpired;
            if (c.getGuild().getId().equals("436670173777362944")) {
                hasNotExpired = petcache.getIfPresent(requestedId) == c.getGuild();
            } else {
                hasNotExpired = defaultCache.getIfPresent(requestedId) == c.getGuild();
            }

            if (hasNotExpired) {
                context.makeError("You've already submitted a remittance request for `:guildName`, please wait 24/48 hours after the last time you've submitted a remittance request.")
                    .set("guildName", c.getGuild().getName()).queue();
                message.editMessage("ERROR. PLEASE CHECK BELOW").setEmbeds(Collections.emptyList()).queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            if (d.getInt("roblox_group_id") != 0) {

                        List <RobloxUserGroupRankService.Data> grs = avaire.getRobloxAPIManager().getUserAPI().getUserRanks(requestedId);

                        if (grs.isEmpty()) {
                            context.makeError("You are not in any groups.").queue();
                            removeAllUserMessages(messagesToRemove);
                            return;
                        }

                        Optional <RobloxUserGroupRankService.Data> b = grs.stream().filter(g -> g.getGroup().getId() == d.getInt("roblox_group_id")).findFirst();

                        if (b.isPresent()) {
                            startConfirmationWaiter(context, message, b, d, content, messagesToRemove);
                        } else {
                            //context.makeInfo(String.valueOf(response.getResponse().code())).queue();
                            context.makeError("You're not in ``:guild``, please check if this is correct or not.").set("guild", d.getString("name")).queue();
                            removeAllUserMessages(messagesToRemove);
                        }

            } else {
                startConfirmationWaiter(context, message, Optional.empty(), d, content, messagesToRemove);
            }
        }
    }

    private void startConfirmationWaiter(CommandMessage context, Message message, Optional <RobloxUserGroupRankService.Data> b, DataRow d, MessageReceivedEvent content, List <Message> messagesToRemove) {
        Button b1 = Button.success("yes:" + message.getId(), "Yes").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.danger("no:" + message.getId(), "No").withEmoji(Emoji.fromUnicode("❌"));


        message.editMessageEmbeds(context.makeInfo("Ok, so. I've collected everything you've told me. And this is the data I got:\n\n" +
            "**Username**: " + context.getMember().getEffectiveName() + "\n" +
            "**Group**: " + d.getString("name") + "\n" + (b.map(data -> "**Rank**: " + data.getRole().getName() + "\n").orElse("\n")) +
            "**Evidence**: \n" + content.getMessage().getContentRaw() +
            "\n\nIs this correct?").buildEmbed()).setActionRow(b1.asEnabled(), b2.asEnabled()).queue(l -> {
            //l.addReaction("✅").queue();
            //l.addReaction("❌").queue();
            avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class, r -> isValidMember(r, context, l), send -> {
                if (send.getButton().getEmoji().getName().equalsIgnoreCase("❌") || send.getButton().getEmoji().getName().equalsIgnoreCase("x")) {
                    message.editMessageEmbeds(context.makeSuccess("Remittance has been canceled, if you want to restart the report. Do ``!pr`` in any bot-commands channel.").buildEmbed()).setActionRows(Collections.emptyList()).queue();
                    removeAllUserMessages(messagesToRemove);
                } else if (send.getButton().getEmoji().getName().equalsIgnoreCase("✅")) {
                    message.editMessage("Remittance has been \"sent\".").setActionRows(Collections.emptyList()).queue();
                    sendReport(context, message, b, d, context.getMember().getEffectiveName(), content.getMessage().getContentRaw(), messagesToRemove);
                    removeAllUserMessages(messagesToRemove);
                } else {
                    message.editMessage("Invalid button given, report deleted!").setActionRows(Collections.emptyList()).queue();
                    removeAllUserMessages(messagesToRemove);
                }
            }, 5, TimeUnit.MINUTES, () -> {
                removeAllUserMessages(messagesToRemove);
                message.editMessage("You took to long to respond, please restart the report system!").queue();
            });
        });
    }

    private void removeAllUserMessages(List <Message> messagesToRemove) {
        for (Message m : messagesToRemove) {
            m.delete().queue();
        }
    }

    private void sendReport(CommandMessage context, Message message, Optional <RobloxUserGroupRankService.Data> groupInfo, DataRow dataRow, String username, String evidence, List <Message> messagesToRemove) {
        TextChannel tc = avaire.getShardManager().getTextChannelById(dataRow.getString("patrol_remittance_channel"));

        if (tc != null) {
            Button b1 = Button.success("accept:" + message.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
            Button b2 = Button.danger("reject:" + message.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
            Button b3 = Button.secondary("remove:" + message.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));


            tc.sendMessageEmbeds(context.makeEmbeddedMessage(new Color(32, 34, 37))
                .setAuthor("Event remittance from: " + username, null, getImageByName(context.guild, username))
                .setDescription(
                    "**Username**: " + username + "\n" +
                        (groupInfo.map(data -> "**Rank**: " + data.getRole().getName() + "\n").orElse("")) +
                        "**Proof**: \n" + evidence)
                .requestedBy(context)
                .setTimestamp(Instant.now())
                .setThumbnail(getImageFromDiscordId(context.getMember().getId()))
                .buildEmbed())
                .setActionRow(b1.asEnabled(), b2.asEnabled(), b3.asEnabled())
                .queue(
                    finalMessage -> {

                        message.editMessageEmbeds(context.makeSuccess("[Your remittance has been created in the correct channel.](:link).").set("link", finalMessage.getJumpUrl())
                            .buildEmbed())
                            .queue();
                        //createReactions(finalMessage);
                        try {
                            avaire.getDatabase().newQueryBuilder(Constants.REMITTANCE_DATABASE_TABLE_NAME).insert(data -> {
                                data.set("pb_server_id", finalMessage.getGuild().getId());
                                data.set("request_message_id", finalMessage.getId());
                                data.set("requester_discord_id", context.getAuthor().getId());
                                data.set("requester_discord_name", context.getMember().getEffectiveName(), true);
                                data.set("requester_roblox_id", getRobloxId(username));
                                data.set("requester_roblox_name", username);
                                data.set("requester_evidence", evidence, true);
                                data.set("requester_roblox_rank", groupInfo.map(value -> value.getRole().getName()).orElse(null));
                            });

                            boolean hasNotExpired;
                            if (finalMessage.getGuild().getId().equals("436670173777362944")) {
                                petcache.put(getRobloxId(username), context.getGuild());
                            } else {
                                defaultCache.put(getRobloxId(username), context.getGuild());
                            }

                        } catch (SQLException throwables) {
                            Xeus.getLogger().error("ERROR: ", throwables);
                        }

                    }
                );
        } else {
            context.makeError("Channel can't be found for the guild ``" + dataRow.getString("name") + "``. Please contact the bot developer, or guild HRs.");
        }
    }

    private String getImageFromDiscordId(String id) {
        VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchVerificationFromDatabase(id, true);
        if (ve == null) {
            return null;
        }

        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }

    private String getImageByName(Guild guild, String username) {
        List <Member> members = guild.getMembersByEffectiveName(username, true);

        if (members.size() < 1) return null;
        if (members.size() > 1) return null;
        else return members.get(0).getUser().getEffectiveAvatarUrl();
    }

    private static boolean isValidMember(ButtonInteractionEvent r, CommandMessage context, Message l) {
        return context.getMember().equals(r.getMember()) && r.getMessageId().equalsIgnoreCase(l.getId());
    }

    private boolean checkAccountAge(CommandMessage context) {
        if (context.member != null) {
            return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - context.member.getUser().getTimeCreated().toInstant().toEpochMilli()) < 3;
        }
        return false;
    }

    private boolean checkEvidenceAcceptance(CommandMessage context, MessageReceivedEvent pm) {
        String message = pm.getMessage().getContentRaw();
        if (!(message.startsWith("https://youtu.be") ||
            message.startsWith("http://youtu.be") ||
            message.startsWith("https://www.youtube.com/") ||
            message.startsWith("http://www.youtube.com/") ||
            message.startsWith("https://youtube.com/") ||
            message.startsWith("http://youtube.com/") ||
            message.startsWith("https://streamable.com/") ||
            message.contains("cdn.discordapp.com") ||
            message.contains("media.discordapp.com"))) {
            pm.getChannel().sendMessageEmbeds(context.makeError("Sorry, but we are only accepting [YouTube links](https://www.youtube.com/upload) or [Streamable](https://streamable.com/) as evidence. Try again").buildEmbed()).queue();
            return false;
        }
        return true;
    }

    private boolean runClearAllChannelsFromDatabase(CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("patrol_remittance_channel", null);
            });

            context.makeSuccess("Any information about the remittance channels has been removed from the database.").queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    private boolean runSetRemittanceChannel(CommandMessage context, String[] args) {
        if (args.length < 3) {
            return sendErrorMessage(context, "Incorrect arguments");
        }
        RichCustomEmoji e;
        GuildChannel c = MentionableUtil.getChannel(context.message, args, 1);
        if (c == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }

        if (NumberUtil.isNumeric(args[1])) {
            e = avaire.getShardManager().getEmojiById(args[1]);
            if (e == null) {
                return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Emote - ID)");
            }
        } else if (context.message.getMentions().getCustomEmojis().size() == 1) {
            e = c.getGuild().getEmojiById(context.message.getMentions().getCustomEmojis().get(0).getId());
            if (e == null) {
                return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Emote - Mention)");
            }
        } else {
            return sendErrorMessage(context, "Something went wrong (To many emotes).");
        }

        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }
        return updateChannelAndEmote(transformer, context, (TextChannel) c, e);
    }


    private boolean updateChannelAndEmote(GuildSettingsTransformer transformer, CommandMessage context, TextChannel channel, RichCustomEmoji emote) {
        transformer.setPatrolRemittance(channel.getId());
    
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("patrol_remittance_channel", transformer.getPatrolRemittanceChannel());
            });

            context.makeSuccess("Remittance have been enabled for :channel with the emote :emote").set("channel", channel.getAsMention()).set("emote", emote.getAsMention()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }


    private Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return 0L;
        }
    }


}
