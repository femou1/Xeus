package com.pinewoodbuilders.commands.pinewood;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.blacklist.features.FeatureScope;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.requests.Request;
import com.pinewoodbuilders.requests.Response;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.components.Button;
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


public class ReportUserCommand extends Command {


    public ReportUserCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Report User Command";
    }

    @Override
    public String getDescription() {
        return "Report a user who is breaking PBST Handbook rules.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Start the report system."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Start the report system."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("report-user", "ru");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.REPORTS
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

        int permissionLevel = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (permissionLevel >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
            if (args.length > 0) {
                switch (args[0].toLowerCase()) {
                    case "sr":
                    case "set-reports":
                        return runSetReportChannel(context, args);
                    case "ca":
                    case "clear-all":
                        return runClearAllChannelsFromDatabase(context);
                    case "sgi":
                    case "set-group-id":
                        return runSetGroupId(context, args);
                    case "srm":
                    case "set-report-message":
                        return runSetReportMessage(context);
                    default:
                        return sendErrorMessage(context, "Please enter in a correct argument.");
                }
            }
        }

        /*if (!(permissionLevel == GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel())) {
            context.makeError("This command is still disabled for normal users, only Permission Level ``" + GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel() + "`` can use this.").queue();
            return true;
        }*/

        if (checkAccountAge(context)) {
            context.makeError("Sorry, but only discord accounts that are older then 3 days are allowed to make actual reports.\nIf this is an important violation, please contact a Lead of the division where you are trying to report.").queue();
            return false;
        }

        context.makeInfo("<a:loading:742658561414266890> Loading reports...").queue(l -> {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).orderBy("handbook_report_channel");
            try {

                StringBuilder sb = new StringBuilder();
                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("handbook_report_channel") != null) {
                        Guild g = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                        Emote e = avaire.getShardManager().getEmoteById(dataRow.getString("emoji_id"));

                        if (g != null && e != null) {
                            sb.append("``").append(g.getName()).append("`` - ").append(e.getAsMention()).append("\n");
                            l.addReaction(e).queue();
                        } else {
                            context.makeError("Either the guild or the emote can't be found in the database, please check with the developer.").queue();
                            return;
                        }
                    }

                });
                l.addReaction("❌").queue();
                l.editMessageEmbeds(context.makeInfo("Welcome to the pinewood report system. With this feature, you can report any Pinewood member in any of the pinewood groups!\n\n" + sb.toString()).buildEmbed()).queue(
                    message -> {
                        avaire.getWaiter().waitForEvent(MessageReactionAddEvent.class, event -> {
                            return event.getMember().equals(context.member) && event.getMessageId().equalsIgnoreCase(message.getId());
                        }, react -> {
                            try {
                                if (react.getReactionEmote().getName().equalsIgnoreCase("❌")) {
                                    message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).queue();
                                    message.clearReactions().queue();
                                    return;
                                }
                                DataRow d = qb.where("emoji_id", react.getReactionEmote().getId()).get().get(0);

                                TextChannel c = avaire.getShardManager().getTextChannelById(d.getString("handbook_report_channel"));
                                if (c != null) {
                                    if (avaire.getFeatureBlacklist().isBlacklisted(context.getAuthor(), c.getGuild().getIdLong(), FeatureScope.REPORTS)) {
                                        message.editMessageEmbeds(context.makeError("You have been blacklisted from creating reports for this guild. Please ask a **Level 4** (Or higher) member to remove you from the ``"+c.getGuild().getName()+"`` reports blacklist.").buildEmbed()).queue();
                                        return;
                                    }
                                    message.editMessageEmbeds(context.makeInfo(d.getString("handbook_report_info_message", "A report message for ``:guild`` could not be found. Ask the HR's of ``:guild`` to set one.\n" +
                                        "If you'd like to report someone, say their name right now.")).set("guild", d.getString("name")).set(":user", context.member.getEffectiveName()).buildEmbed()).queue(
                                        nameMessage -> {
                                            avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, m -> {
                                                    return m.getMember() != null && m.getMember().equals(context.member) && message.getChannel().equals(l.getChannel());
                                                },
                                                content -> {
                                                goToStep2(context, message, content, d, c);
                                                },
                                                90, TimeUnit.SECONDS,
                                                () -> message.editMessage("You took to long to respond, please restart the report system!").queue());


                                        }
                                    );
                                    message.clearReactions().queue();
                                } else {
                                    context.makeError("The guild doesn't have a (valid) channel for suggestions").queue();
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

        //context.makeInfo(context.getGuildSettingsTransformer().getHandbookReportInfoMessage()).set("user", context.getMember().getEffectiveName()).set("guild", ).queue();

        return false;
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
            List<Message> messagesToRemove = new ArrayList<>();
            messagesToRemove.add(content.getMessage());
            if (content.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            Long requestedId = getRobloxId(content.getMessage().getContentRaw());
            if (requestedId == 0L) {
                context.makeError("Sorry, but your username you gave us, does not exist on roblox. Please give us a username that's on roblox").queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            boolean isBlacklisted = checkIfBlacklisted(requestedId, c);
            if (isBlacklisted) {
                message.editMessageEmbeds(context.makeWarning("This user is already blacklisted in ``" + c.getGuild().getName() + "``.").buildEmbed()).queue();
                removeAllUserMessages(messagesToRemove);
                return;
            }

            if (!d.getString("id").equalsIgnoreCase("371062894315569173") && d.getInt("roblox_group_id") != 0) {
                Request requestedRequest = RequestFactory.makeGET("https://groups.roblox.com/v1/users/" + requestedId + "/groups/roles");
                requestedRequest.send((Consumer <Response>) response -> {
                    if (response.getResponse().code() == 200) {
                        RobloxUserGroupRankService grs = (RobloxUserGroupRankService) response.toService(RobloxUserGroupRankService.class);
                        Optional <RobloxUserGroupRankService.Data> b = grs.getData().stream().filter(g -> g.getGroup().getId() == d.getInt("roblox_group_id")).findFirst();

                        if (b.isPresent()) {
                            message.editMessageEmbeds(context.makeInfo(
                                "You're trying to report: ``:reported``\n" +
                                    "With the rank: ``:rank``\n\nPlease tell me what he did wrong. (Make sure this is an actual handbook violation)").set("reported", content.getMessage().getContentRaw()).set("rank", b.get().getRole().getName()).buildEmbed()).queue(
                                getEvidence -> {
                                    startDescriptionWaiter(context, message, b, d, getEvidence, content, messagesToRemove);
                                }
                            );
                        } else {
                            //context.makeInfo(String.valueOf(response.getResponse().code())).queue();
                            context.makeError("The user who you've requested a reward for isn't in ``:guild``, please check if this is correct or not.").set("guild", d.getString("name")).queue();
                            removeAllUserMessages(messagesToRemove);
                        }
                    }
                });
            } else {
                message.editMessageEmbeds(context.makeInfo(
                    "You're trying to report: ``:reported``\n" +
                        "\nPlease tell me what he did wrong. (Make sure this is an actual handbook violation)").set("reported", content.getMessage().getContentRaw()).buildEmbed()).queue(
                    getEvidence -> {
                        startDescriptionWaiter(context, message, Optional.empty(), d, getEvidence, content, messagesToRemove);
                    }
                );
            }
        }
    }

    private void removeAllUserMessages(List<Message> messagesToRemove) {
        for (Message m : messagesToRemove) {
            m.delete().queue();
        }
    }

    private void startDescriptionWaiter(CommandMessage context, Message message, Optional <RobloxUserGroupRankService.Data> b, DataRow d, Message getEvidence, MessageReceivedEvent content, List <Message> messagesToRemove) {
        avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, a ->
                context.getMember().equals(a.getMember()) && antiSpamInfo(context, a),
            r -> {
                messagesToRemove.add(r.getMessage());
                if (r.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                    message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).queue();
                    removeAllUserMessages(messagesToRemove);
                    return;
                }

                startEvidenceWaiter(context, r.getMessage().getContentRaw(), message, b, d, content.getMessage().getContentRaw(), messagesToRemove);
            },
            5, TimeUnit.MINUTES,
            () -> {
                message.editMessage("You took to long to respond, please restart the report system!").queue();
                removeAllUserMessages(messagesToRemove);
            });
    }

    private void startEvidenceWaiter(CommandMessage context, String contentRaw, Message message, Optional <RobloxUserGroupRankService.Data> groupInfo, DataRow dataRow, String username, List <Message> messagesToRemove) {
        message.editMessageEmbeds(context.makeSuccess("I've collected the violation you entered, but I need to be sure he actually did something bad.\n" +
            "Please enter a **LINK** to evidence.\n\n" +
            "**We're accepting**:\n" +
            "- [YouTube Links](https://www.youtube.com/upload)\n" +
            "- [Imgur Links](https://imgur.com/upload)\n" +
            "- [Discord Image Links](https://cdn.discordapp.com/attachments/689520756891189371/733599719351123998/unknown.png)\n" +
            "- [Gyazo Links](https://gyazo.com)\n" +
            "- [LightShot Links](https://app.prntscr.com/)\n" +
            "- [Streamable](https://streamable.com)\n" +
            "If you want a link/video/image service added, please ask ``Stefano#7366``").buildEmbed()).queue(evi -> avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, pm ->
                context.getMember().equals(pm.getMember()) && context.getChannel().equals(pm.getChannel()) && checkEvidenceAcceptance(context, pm),
            evidence -> {
                messagesToRemove.add(evidence.getMessage());
                if (evidence.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                    message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).queue();
                    removeAllUserMessages(messagesToRemove);
                    return;
                }
                startConfirmWarnedEvidence(context, message, groupInfo, dataRow, username, evidence.getMessage().getContentRaw(), contentRaw, messagesToRemove);
            },
            5, TimeUnit.MINUTES,
            () -> {
                message.editMessage("You took to long to respond, please restart the report system!").queue();
                removeAllUserMessages(messagesToRemove);
            }));

    }

    private void startConfirmWarnedEvidence(CommandMessage context, Message message, Optional<RobloxUserGroupRankService.Data> groupInfo, DataRow dataRow, String username, String contentRaw, String evidence, List<Message> messagesToRemove) {
        if (!(dataRow.getString("id").equalsIgnoreCase("572104809973415943") || dataRow.getString("id").equalsIgnoreCase("371062894315569173"))) {
            message.editMessageEmbeds(context.makeWarning("You've given evidence about reporting someone, **however** we now need proof that they did something wrong.\n" +
                "Please enter a **LINK** to evidence to proof you've warned the user about their misbehavior.\n\n" +
                "**We're accepting**:\n" +
                "- [YouTube Links](https://www.youtube.com/upload)\n" +
                "- [Imgur Links](https://imgur.com/upload)\n" +
                "- [Discord Image Links](https://cdn.discordapp.com/attachments/689520756891189371/733599719351123998/unknown.png)\n" +
                "- [Gyazo Links](https://gyazo.com)\n" +
                "- [LightShot Links](https://app.prntscr.com/)\n" +
                "- [Streamable](https://streamable.com)\n" +
                "If you want a link/video/image service added, please ask ``Stefano#7366``").buildEmbed()).queue(evi -> avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, pm ->
                    context.getMember().equals(pm.getMember()) && context.getChannel().equals(pm.getChannel()) && checkEvidenceAcceptance(context, pm),
                explainedEvidence -> {
                    messagesToRemove.add(explainedEvidence.getMessage());
                    startConfirmationWaiter(context, message, groupInfo, dataRow, username, contentRaw, evidence, messagesToRemove, explainedEvidence.getMessage().getContentRaw());
                },
                5, TimeUnit.MINUTES,
                () -> {
                    message.editMessage("You took to long to respond, please restart the report system!").queue();
                    removeAllUserMessages(messagesToRemove);
                }));
        } else {
            startConfirmationWaiter(context, message, groupInfo, dataRow, username, contentRaw, evidence, messagesToRemove, null);
        }




    }

    private void startConfirmationWaiter(CommandMessage context, Message message, Optional<RobloxUserGroupRankService.Data> groupInfo, DataRow dataRow, String username, String evidence, String description, List<Message> messagesToRemove, String explainedEvidence) {

        Button b1 = Button.success("yes", "Yes").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.danger("no", "No").withEmoji(Emoji.fromUnicode("❌"));

        message.editMessageEmbeds(context.makeInfo("Ok, so. I've collected everything you've told me. And this is the data I got:\n\n" +
            "**Username**: " + username + "\n" +
            "**Group**: " + dataRow.getString("name") + "\n" + (groupInfo.map(data -> "**Rank**: " + data.getRole().getName() + "\n").orElse("\n")) +
            "**Description**: \n" + description + "\n\n" +
            "**Evidence**: \n" + evidence +
            (explainedEvidence != null ? "\n\n**Evidence of warning**:\n" + explainedEvidence : "") + "\n\nIs this correct?").buildEmbed())
            .setActionRow(b1.asEnabled(), b2.asEnabled()).queue(l -> {

            avaire.getWaiter().waitForEvent(ButtonClickEvent.class, r -> isValidMember(r, context, l), send -> {
                if (send.getButton().getEmoji().getName().equalsIgnoreCase("❌") || send.getButton().getEmoji().getName().equalsIgnoreCase("x")) {
                    message.editMessage("Report has been canceled, if you want to restart the report. Do ``!ru`` in any bot-commands channel.").setActionRows(Collections.emptyList()).queue();
                    removeAllUserMessages(messagesToRemove);
                } else if (send.getButton().getEmoji().getName().equalsIgnoreCase("✅")) {
                    message.editMessage("Report has been \"sent\".").queue();
                    sendReport(context, message, groupInfo, dataRow, username, description, evidence, explainedEvidence, send);
                    removeAllUserMessages(messagesToRemove);
                } else {
                    message.editMessage("Invalid emoji given, report deleted!").setActionRows(Collections.emptyList()).queue();
                    removeAllUserMessages(messagesToRemove);
                }
            }, 5, TimeUnit.MINUTES, () -> {
                removeAllUserMessages(messagesToRemove);
                message.editMessage("You took to long to respond, please restart the report system!").queue();
            });
        });
    }

    private void sendReport(CommandMessage context, Message message, Optional<RobloxUserGroupRankService.Data> groupInfo, DataRow dataRow, String username, String description, String evidence, String explainedEvidence, ButtonClickEvent send) {
        TextChannel tc = avaire.getShardManager().getTextChannelById(dataRow.getString("handbook_report_channel"));

        if (tc != null) {

            Button b1 = Button.success("accept:" + message.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
            Button b2 = Button.danger("reject:" + message.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
            Button b3 = Button.secondary("remove:" + message.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));

            tc.sendMessageEmbeds(context.makeEmbeddedMessage(new Color(32, 34, 37))
                .setAuthor("Report created for: " + username, null, getImageByName(context.guild, username))
                .setDescription(
                    "**Violator**: " + username + "\n" +
                        (groupInfo.map(data -> "**Rank**: " + data.getRole().getName() + "\n").orElse("")) +
                        "**Information**: \n" + description + "\n\n" +
                        "**Evidence**: \n" + evidence +
                        (explainedEvidence != null ? "\n**Evidence of warning**:\n" + explainedEvidence : ""))
                .requestedBy(context)
                .setTimestamp(Instant.now())
                .buildEmbed()).setActionRow(b1.asEnabled(), b2.asEnabled(), b3.asEnabled())
                .queue(
                    finalMessage -> {
                        message.editMessageEmbeds(context.makeSuccess("[Your report has been created in the correct channel.](:link).").set("link", finalMessage.getJumpUrl())
                            .buildEmbed()).setActionRows(Collections.emptyList())
                            .queue();
                        createReactions(finalMessage);
                        try {
                            avaire.getDatabase().newQueryBuilder(Constants.REPORTS_DATABASE_TABLE_NAME).insert(data -> {
                                data.set("pb_server_id", finalMessage.getGuild().getId());
                                data.set("report_message_id", finalMessage.getId());
                                data.set("reporter_discord_id", context.getAuthor().getId());
                                data.set("reporter_discord_name", context.getMember().getEffectiveName(), true);
                                data.set("reported_roblox_id", getRobloxId(username));
                                data.set("reported_roblox_name", username);
                                data.set("report_evidence", evidence, true);
                                data.set("report_evidence_warning", explainedEvidence, true);
                                data.set("report_reason", description, true);
                                data.set("reported_roblox_rank", groupInfo.map(value -> value.getRole().getName()).orElse(null));
                            });
                        } catch (SQLException throwables) {
                            Xeus.getLogger().error("ERROR: ", throwables);
                        }

                    }
                );

        } else {
            context.makeError("Channel can't be found for the guild ``" + dataRow.getString("name") + "``. Please contact the bot developer, or guild HRs.").queue();
        }
    }

    private String getImageByName(Guild guild, String username) {
        List<Member> members = guild.getMembersByEffectiveName(username, true);

        if (members.size() < 1) return null;
        if (members.size() > 1) return null;
        else return members.get(0).getUser().getEffectiveAvatarUrl();
    }

    private static boolean isValidMember(ButtonClickEvent r, CommandMessage context, Message l) {
        return context.getMember().equals(r.getMember()) && r.getMessageId().equalsIgnoreCase(l.getId());
    }

    private boolean runSetReportMessage(CommandMessage context) {
        context.makeInfo("Please tell me, what would you like as the guild report message?").queue(message -> {
            avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, m -> m.getMember().equals(context.member) && message.getChannel().equals(m.getChannel()), reportMessage -> {
                QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
                try {
                    qb.update(q -> {
                        q.set("handbook_report_info_message", reportMessage.getMessage().getContentRaw(), true);
                    });
                    context.makeSuccess("**Your guild's message has been set to**: \n" + reportMessage.getMessage().getContentRaw()).queue();
                    return;
                } catch (SQLException throwables) {
                    context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
                    Xeus.getLogger().error("ERROR: ", throwables);
                    return;
                }
            },
                5, TimeUnit.MINUTES,
                () -> {
                    message.editMessage("You took to long to respond, please restart the report system!").queue();
                });
        });
        return false;
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
            message.contains("media.discordapp.com") ||
            message.contains("gyazo.com") ||
            message.contains("prntscr.com") ||
            message.contains("prnt.sc") || message.contains("imgur.com"))) {
            pm.getChannel().sendMessageEmbeds(context.makeError("Sorry, but we are only accepting [YouTube links](https://www.youtube.com/upload), [Gyazo Links](https://gyazo.com), [LightShot Links](https://app.prntscr.com/), [Discord Image Links](https://cdn.discordapp.com/attachments/689520756891189371/733599719351123998/unknown.png) or [Imgur links](https://imgur.com/upload) as evidence. Try again").buildEmbed()).queue();
            return false;
        }
        return true;
    }

    private boolean antiSpamInfo(CommandMessage context, MessageReceivedEvent l) {
        if (l.getMessage().getContentRaw().equalsIgnoreCase("cancel")) return true;

        int length = l.getMessage().getContentRaw().length();
        if (length <= 25 || length >= 700) {
            context.getChannel().sendMessageEmbeds(context.makeError("Sorry, but reports have to have a minimal of 25 characters and a maximum of 700 characters.\n" +
                "Your report currently has **``" + length + "``** characters").buildEmbed()).queue();
            return false;

        } else {
            return true;
        }
    }

    private boolean runClearAllChannelsFromDatabase(CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("handbook_report_channel", null);
                q.set("emoji_id", null);
                q.set("handbook_report_info_message", null);
            });

            context.makeSuccess("Any information about the suggestion channel has been removed from the database.").queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    private boolean runSetReportChannel(CommandMessage context, String[] args) {
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

    private boolean runSetGroupId(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }

        if (NumberUtil.isNumeric(args[1])) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer == null) {
                context.makeError("I can't pull the guilds information, please try again later.").queue();
                return false;
            }
            transformer.setMainGroupId(Integer.parseInt(args[1]));
            return updateGroupId(transformer, context);
        } else {
            return sendErrorMessage(context, "Something went wrong, please check if you ran the command correctly.");
        }


    }

    private boolean updateGroupId(GuildSettingsTransformer transformer, CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {   
                q.set("roblox_group_id", transformer.getRobloxGroupId());
            });

            context.makeSuccess("Set the ID for ``:guild`` to ``:id``").set("guild", context.getGuild().getName()).set("id", transformer.getRobloxGroupId()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }
    }


    private boolean updateChannelAndEmote(GuildSettingsTransformer transformer, CommandMessage context, TextChannel channel, Emote emote) {
        transformer.setHandbookReportChannel(channel.getIdLong());
        
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("handbook_report_channel", transformer.getHandbookReportChannel());
            });

            context.makeSuccess("Suggestions have been enabled for :channel").set("channel", channel.getAsMention()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    public static void createReactions(Message r) {
        r.addReaction("\uD83D\uDC4D").queue();   // 👍
        r.addReaction("\uD83D\uDC4E").queue();  // 👎
/*        r.addReaction("✅").queue();
        r.addReaction("❌").queue();
        r.addReaction("🚫").queue();
        r.addReaction("\uD83D\uDD04").queue(); // 🔄*/
    }

    private static Long getRobloxId(String un) {
        try {
            JSONObject json = readJsonFromUrl("http://api.roblox.com/users/get-by-username?username=" + un);
            return Double.valueOf(json.getDouble("Id")).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void tookToLong(CommandMessage event) {
        event.makeError("<a:alerta:729735220319748117> You've taken to long to react to the message <a:alerta:729735220319748117>").queue();
    }
}
