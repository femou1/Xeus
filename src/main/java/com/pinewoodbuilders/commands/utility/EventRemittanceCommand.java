package com.pinewoodbuilders.commands.utility;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.blacklist.features.FeatureScope;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.requests.Request;
import com.pinewoodbuilders.requests.Response;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.pinewoodbuilders.utilities.JsonReader.readJsonFromUrl;

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
            return sendErrorMessage(context, "Something went wrong loading the guild settings. Please try again later.");
        }
        if (args.length > 0) {
            if (XeusPermissionUtil.getPermissionLevel(context).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
                return switch (args[0].toLowerCase()) {
                    case "sc", "set-channel" -> runSetRemittanceChannel(context, args);
                    case "clear", "reset" -> runClearAllChannelsFromDatabase(context);
                    default -> sendErrorMessage(context, "Invalid argument given.");
                };
            }
        }

        return startRemittanceWaiter(context, args);
    }

    private boolean startRemittanceWaiter(CommandMessage context, String[] args) {
        if (checkAccountAge(context)) {
            return sendErrorMessage(context, "Sorry, but only discord accounts that are older then 3 days are allowed to make remittance requests.");
        }
        IThreadContainer tc = ((IThreadContainer) context.getGuildChannel());
        tc.createThreadChannel(context.getMember().getEffectiveName() + " remittance command thread.", context.message.getId()).queue(channel -> messageInThread(channel, context.getMember()));
        return true;
    }

    private void messageInThread(ThreadChannel channel, Member member) {
        MessageFactory.makeEmbeddedMessage(channel).setDescription("<a:loading:742658561414266890> Loading servers that are using event remittance...").queue(l -> {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).orderBy("patrol_remittance_channel");
            try {
                StringBuilder sb = new StringBuilder();
                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("patrol_remittance_channel") != null) {
                        Guild g = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                        Emote e = avaire.getShardManager().getEmoteById(dataRow.getString("emoji_id"));

                        if (g != null && e != null) {
                            sb.append("``").append(g.getName()).append("`` - ").append(e.getAsMention()).append("\n");
                            l.addReaction(e).queue();
                        } else {
                            l.editMessageEmbeds(new EmbedBuilder().setDescription("Either the guild or the emote can't be found in the database, please check with the developer.").setFooter("This thread will delete in 10 seconds.").build()).queue();
                            channel.delete().queueAfter(10, TimeUnit.SECONDS);
                        }
                    }
                });
                l.addReaction("❌").queue();
                l.editMessageEmbeds(new EmbedBuilder().setDescription("Welcome to the event remittance system. With this feature, you can record your patrolling/raiding for groups that have this enabled! (Please check the rules regarding what events they allow remittance for)\n" +
                    "\n" + sb).build()).queue(
                    message -> {
                        avaire.getWaiter().waitForEvent(MessageReactionAddEvent.class, event -> {
                                return event.isFromThread() && event.getMember().equals(member) && event.getMessageId().equalsIgnoreCase(message.getId());
                            }, react -> {
                                try {
                                    if (react.getReactionEmote().getName().equalsIgnoreCase("❌")) {
                                        message.editMessageEmbeds(new EmbedBuilder().setDescription("Cancelled the system").build()).queue();
                                        message.clearReactions().queue();
                                        channel.delete().queueAfter(10, TimeUnit.SECONDS);
                                        return;
                                    }
                                    DataRow d = qb.where("emoji_id", react.getReactionEmote().getId()).get().get(0);

                                    TextChannel c = avaire.getShardManager().getTextChannelById(d.getString("patrol_remittance_channel"));
                                    if (c != null) {
                                        if (avaire.getFeatureBlacklist().isBlacklisted(member.getUser(), c.getGuild().getIdLong(), FeatureScope.PATROL_REMITTANCE)) {
                                            message.editMessageEmbeds(new EmbedBuilder().setDescription("You have been blacklisted from requesting a remittance for this guild. Please ask a **Level 4** (Or higher) member to remove you from the ``" + c.getGuild().getName() + "`` remittance blacklist.").build()).queue();
                                            return;
                                        }
                                        message.editMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), d.getString("patrol_remittance_message", """
                                            A remittance message for ``:guild`` could not be found. Ask the HR's of ``:guild`` to set one.
                                            If you'd like to request remittance, please enter evidence of this in right now.``` ```

                                            Please enter a **LINK** to evidence.\s
                                            **Remember, you may only post once per 24 hours. The video may *only be 2 hours* and has to have a *minimum of 30 minutes* in duration**

                                            **We're accepting**:
                                            - [YouTube Links](https://www.youtube.com/upload)
                                            - [Streamable](https://streamable.com)
                                            If you want a link/video service added, please ask ``Stefano#7366``"""))
                                            .set("guild", d.getString("name"))
                                            .set(":user", member.getEffectiveName()).buildEmbed()).queue(
                                            nameMessage -> {
                                                avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, m -> m.getMember().equals(member) && message.getChannel().equals(l.getChannel()) && checkEvidenceAcceptance(m),
                                                    content -> {
                                                        goToStep2(channel, member, message, content, d, c);
                                                    },
                                                    90, TimeUnit.SECONDS,
                                                    () -> message.editMessage("You took to long to respond, please restart the remittace system!").queue());
                                            }
                                        );
                                        message.clearReactions().queue();
                                    } else {
                                        message.editMessageEmbeds(new EmbedBuilder().setDescription("The guild doesn't have a (valid) channel for remittance").build()).queue();
                                    }

                                } catch (SQLException throwables) {
                                    message.editMessageEmbeds(new EmbedBuilder().setDescription("Something went wrong while checking the database, please check with the developer for any errors.").build()).queue();
                                }
                            },
                            5, TimeUnit.MINUTES,
                            () -> {
                                message.editMessage("You took to long to respond, please restart the report system!").queue();
                                channel.delete().queueAfter(10, TimeUnit.SECONDS);
                            });
                    }
                );

            } catch (SQLException throwables) {
                l.editMessageEmbeds(new EmbedBuilder().setDescription("Something went wrong while checking the database, please check with the developer for any errors.").build()).queue();
            }
        });
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

    private void goToStep2(ThreadChannel threadChannel, Member member, Message message, MessageReceivedEvent content, DataRow d, TextChannel c) {
        {
            if (content.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                message.editMessageEmbeds(new EmbedBuilder().setDescription("Cancelled the system").build()).queue();
                removeAllUserMessages(threadChannel);
                return;
            }

            VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(member.getId());
            if (ve == null) {
                message.editMessageEmbeds(new EmbedBuilder().setDescription("Sorry, but you are not linked to our databases in any way. Please run `!verify` to get your nickname updated.").build()).queue();
                removeAllUserMessages(threadChannel);
                return;
            }

            Long requestedId = ve.getRobloxId();
            boolean isBlacklisted = checkIfBlacklisted(requestedId, c);
            if (isBlacklisted) {
                message.editMessageEmbeds(new EmbedBuilder().setDescription("You're blacklisted in ``" + c.getGuild().getName() + "``, for this reason you will not be allowed to request a remittance.").build()).queue();
                removeAllUserMessages(threadChannel);
                return;
            }

            boolean hasNotExpired;
            if (c.getGuild().getId().equals("436670173777362944")) {
                hasNotExpired = petcache.getIfPresent(requestedId) == c.getGuild();
            } else {
                hasNotExpired = defaultCache.getIfPresent(requestedId) == c.getGuild();
            }

            if (hasNotExpired) {
                message.editMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "You've already submitted a remittance request for `:guildName`, please wait 24/48 hours after the last time you've submitted a remittance request.")
                    .set("guildName", c.getGuild().getName()).buildEmbed()).queue();
                message.editMessage("ERROR. PLEASE CHECK BELOW").setEmbeds(Collections.emptyList()).queue();
                removeAllUserMessages(threadChannel);
                return;
            }

            if (d.getInt("roblox_group_id") != 0) {
                Request requestedRequest = RequestFactory.makeGET("https://groups.roblox.com/v1/users/" + requestedId + "/groups/roles");
                requestedRequest.send((Consumer <Response>) response -> {
                    if (response.getResponse().code() == 200) {
                        RobloxUserGroupRankService grs = (RobloxUserGroupRankService) response.toService(RobloxUserGroupRankService.class);
                        Optional <RobloxUserGroupRankService.Data> b = grs.getData().stream().filter(g -> g.getGroup().getId() == d.getInt("roblox_group_id")).findFirst();

                        if (b.isPresent()) {
                            startConfirmationWaiter(threadChannel, member, message, b, d, content);
                        } else {
                            //context.makeInfo(String.valueOf(response.getResponse().code())).queue();
                            message.editMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "You're not in ``:guild``, please check if this is correct or not.").set("guild", d.getString("name")).buildEmbed()).queue();
                            removeAllUserMessages(threadChannel);
                        }
                    }
                });
            } else {
                startConfirmationWaiter(threadChannel, member, message, Optional.empty(), d, content);
            }
        }
    }

    private void startConfirmationWaiter(ThreadChannel threadChannel, Member member, Message message, Optional <RobloxUserGroupRankService.Data> b, DataRow d, MessageReceivedEvent content) {
        Button b1 = Button.success("yes:" + message.getId(), "Yes").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.danger("no:" + message.getId(), "No").withEmoji(Emoji.fromUnicode("❌"));

        message.editMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "Ok, so. I've collected everything you've told me. And this is the data I got:\n\n" +
            "**Username**: " + member.getEffectiveName() + "\n" +
            "**Group**: " + d.getString("group_name") + "\n" + (b.map(data -> "**Rank**: " + data.getRole().getName() + "\n").orElse("\n")) +
            "**Evidence**: \n" + content.getMessage().getContentRaw() +
            "\n\nIs this correct?").buildEmbed()).setActionRow(b1.asEnabled(), b2.asEnabled()).queue(l -> {
            //l.addReaction("✅").queue();
            //l.addReaction("❌").queue();
            avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class, r -> isValidMember(r, member, l), send -> {
                if (send.getButton().getEmoji().getName().equalsIgnoreCase("❌") || send.getButton().getEmoji().getName().equalsIgnoreCase("x")) {
                    message.editMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(),"Remittance has been canceled, if you want to restart the report. Do ``!pr`` in any bot-commands channel.").buildEmbed()).setActionRows(Collections.emptyList()).queue();
                    removeAllUserMessages(threadChannel);
                } else if (send.getButton().getEmoji().getName().equalsIgnoreCase("✅")) {
                    message.editMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "Remittance has been \"sent\".").buildEmbed()).setActionRows(Collections.emptyList()).queue();
                    sendReport(threadChannel, member, message, b, d, content.getMessage().getContentRaw());
                    removeAllUserMessages(threadChannel);
                } else {
                    message.editMessage("Invalid button given, report deleted!").setActionRows(Collections.emptyList()).queue();
                    removeAllUserMessages(threadChannel);
                }
            }, 5, TimeUnit.MINUTES, () -> {
                removeAllUserMessages(threadChannel);
                message.editMessage("You took to long to respond, please restart the report system!").queue();
            });
        });
    }

    private void removeAllUserMessages(ThreadChannel threadChannel) {
        threadChannel.delete().queueAfter(10, TimeUnit.SECONDS);
    }

    private void sendReport(ThreadChannel channel, Member member, Message message, Optional <RobloxUserGroupRankService.Data> groupInfo, DataRow dataRow, String evidence) {
        TextChannel tc = avaire.getShardManager().getTextChannelById(dataRow.getString("patrol_remittance_channel"));

        if (tc != null) {
            VerificationEntity verEntity = avaire.getRobloxAPIManager().getVerification().fetchVerification(member.getUser().getId(), true);
            if (verEntity == null) {
                verEntity = new VerificationEntity(2493467086L, "PB_XBot", member.getIdLong(), "DISCORD");
            }

            Button b1 = Button.success("accept:" + message.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
            Button b2 = Button.danger("reject:" + message.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
            Button b3 = Button.secondary("remove:" + message.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));

            String username = verEntity.getRobloxUsername();

            VerificationEntity finalVerEntity = verEntity;
            tc.sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "**Username**: " + username + "\n" +
                (groupInfo.map(data -> "**Rank**: " + data.getRole().getName() + "\n").orElse("")) +
                "**Proof**: \n" + evidence).setColor(new Color(32, 34, 37))
                .setAuthor("Event remittance from: " + username, null, getImageByName(tc.getGuild(), username))
                .requestedBy(member)
                .setTimestamp(Instant.now())
                .setThumbnail(getImageFromDiscordId(member.getId()))
                .buildEmbed())
                .setActionRow(b1.asEnabled(), b2.asEnabled(), b3.asEnabled())
                .queue(
                    finalMessage -> {

                        message.editMessageEmbeds(
                            new PlaceholderMessage(new EmbedBuilder(), "[Your remittance has been created in the correct channel.](:link).")
                                .set("link", finalMessage.getJumpUrl()).buildEmbed())
                            .queue();
                        //createReactions(finalMessage);
                        try {
                            avaire.getDatabase().newQueryBuilder(Constants.REMITTANCE_DATABASE_TABLE_NAME).insert(data -> {
                                data.set("pb_server_id", finalMessage.getGuild().getId());
                                data.set("request_message_id", finalMessage.getId());
                                data.set("requester_discord_id", member.getUser().getId());
                                data.set("requester_discord_name", member.getEffectiveName(), true);
                                data.set("requester_roblox_id", finalVerEntity.getRobloxId());
                                data.set("requester_roblox_name", username);
                                data.set("requester_evidence", evidence, true);
                                data.set("requester_roblox_rank", groupInfo.map(value -> value.getRole().getName()).orElse(null));
                            });


                            if (finalMessage.getGuild().getId().equals("436670173777362944")) {
                                petcache.put(getRobloxId(username), channel.getGuild());
                            } else {
                                defaultCache.put(getRobloxId(username), channel.getGuild());
                            }

                        } catch (SQLException throwables) {
                            Xeus.getLogger().error("ERROR: ", throwables);
                        }

                    }
                );
        } else {
            message.editMessageEmbeds(new EmbedBuilder().setDescription("Channel can't be found for the guild ``" + dataRow.getString("name") + "``. Please contact the bot developer, or guild HRs.").build()).queue();
            channel.delete().queueAfter(10, TimeUnit.SECONDS);
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

    private static boolean isValidMember(ButtonInteractionEvent r, Member member, Message l) {
        return member.equals(r.getMember()) && r.getMessageId().equalsIgnoreCase(l.getId());
    }

    private boolean checkAccountAge(CommandMessage context) {
        if (context.member != null) {
            return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - context.member.getUser().getTimeCreated().toInstant().toEpochMilli()) < 3;
        }
        return false;
    }

    private boolean checkEvidenceAcceptance(MessageReceivedEvent pm) {
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
            pm.getChannel().sendMessageEmbeds(new EmbedBuilder().setDescription("Sorry, but we are only accepting [YouTube links](https://www.youtube.com/upload) or [Streamable](https://streamable.com/) as evidence. Try again").build()).queue();
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
        Emote e;
        GuildChannel c = MentionableUtil.getChannel(context.message, args, 1);
        if (c == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }

        if (NumberUtil.isNumeric(args[1])) {
            e = avaire.getShardManager().getEmoteById(args[1]);
            if (e == null) {
                return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Emote - ID)");
            }
        } else if (context.message.getEmotes().size() == 1) {
            e = context.message.getEmotes().get(0);
            if (e == null) {
                return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Emote - Mention)");
            }
        } else {
            return sendErrorMessage(context, "Something went wrong (To many emotes).");
        }

        if (NumberUtil.isNumeric(args[1])) {
            e = avaire.getShardManager().getEmoteById(args[1]);
            if (e == null) {
                return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Emote - ID)");
            }
        } else if (context.message.getEmotes().size() == 1) {
            e = context.message.getEmotes().get(0);
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


    private boolean updateChannelAndEmote(GuildSettingsTransformer transformer, CommandMessage context, TextChannel channel, Emote emote) {
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

    public static void createReactions(Message r) {
        r.addReaction("\uD83D\uDC4D").queue();   // 👍
        r.addReaction("\uD83D\uDC4E").queue();  // 👎
        r.addReaction("✅").queue();
        r.addReaction("❌").queue();
        r.addReaction("🚫").queue();
        r.addReaction("\uD83D\uDD04").queue(); // 🔄
    }

    private static Long getRobloxId(String un) {
        try {
            JSONObject json = readJsonFromUrl("https://api.roblox.com/users/get-by-username?username=" + un);
            return Double.valueOf(json.getDouble("Id")).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }


}
