package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
                return switch (args[0].toLowerCase()) {
                    case "sr", "set-reports" -> runSetReportChannel(context, args);
                    case "ca", "clear-all" -> runClearAllChannelsFromDatabase(context);
                    case "sgi", "set-group-id" -> runSetGroupId(context, args);
                    case "srm", "set-report-message" -> runSetReportMessage(context);
                    default -> sendErrorMessage(context, "Please enter in a correct argument.");
                };
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

                SelectMenu.Builder menu = SelectMenu.create("selector:server-to-report-to:" + context.getMember().getId() + ":" + context.getMessage().getId())
                    .setPlaceholder("Select the group to report to!") // shows the placeholder indicating what this menu is for
                    .addOption("Cancel", "cancel", "Stop reporting someone", Emoji.fromUnicode("❌"))
                    .setRequiredRange(1, 1); // only one can be selected

                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("handbook_report_channel") != null) {
                        Guild g = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                        Emote e = avaire.getShardManager().getEmoteById(dataRow.getString("emoji_id"));

                        if (g != null && e != null) {
                            menu.addOption(g.getName(), g.getId() + ":" + e.getId(), "Report to " + g.getName(), Emoji.fromEmote(e));
                            /*sb.append("``").append(g.getName()).append("`` - ").append(e.getAsMention()).append("\n");
                            l.addReaction(e).queue();*/
                        } else {
                            context.makeError("Either the guild or the emote can't be found in the database, please check with the developer.").queue();
                        }
                    }
                    //l.addReaction("❌").queue();
                });


                l.editMessageEmbeds(context.makeInfo("""
                    Welcome to the pinewood report system. With this feature, you can report any Pinewood member in any of the pinewood groups!

                    ***__Please choose the group you would like to report to here__***""").buildEmbed()).setActionRow(menu.build()).queue(
                    message -> {
                        avaire.getWaiter().waitForEvent(SelectMenuInteractionEvent.class, interaction -> {
                                return interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel) && interaction.getMessage().equals(message);
                            }, select -> {
                                if (select.getInteraction().getSelectedOptions().get(0).getEmoji().getName().equalsIgnoreCase("❌")) {
                                    message.editMessageEmbeds(context.makeWarning("Cancelled the system").buildEmbed()).setActionRows(Collections.emptySet()).queue();
                                    /*message.clearReactions().queue();*/
                                    return;
                                }

                                String[] split = select.getInteraction().getSelectedOptions().get(0).getValue().split(":");
                                String guildId = split[0];

                                TextInput username = TextInput.create("username", "Username", TextInputStyle.SHORT)
                                    .setPlaceholder("Enter the username of the person you would like to report.")
                                    .setMinLength(3)
                                    .setMaxLength(32) // or setRequiredRange(10, 100)
                                    .build();

                                TextInput reason = TextInput.create("reason", "What has this user done?", TextInputStyle.PARAGRAPH)
                                    .setPlaceholder("Please explain why this is a report.")
                                    .setMinLength(5)
                                    .setMaxLength(500)
                                    .build();

                                TextInput evidence = TextInput.create("evidence", "Please provide some evidence.", TextInputStyle.PARAGRAPH)
                                    .setPlaceholder("We need proof to punish this person. (YT/Imgur/Discord/Gyazo/LightShot/Streamable)")
                                    .setMinLength(5)
                                    .setMaxLength(750)
                                    .build();


                                TextInput proofOfWarning = TextInput.create("proofOfWarning", "Have you warned this person?", TextInputStyle.PARAGRAPH)
                                    .setPlaceholder("We need proof of the user knowing the violation. (YT/Imgur/Discord/Gyazo/LightShot/Streamable)")
                                    .setMinLength(5)
                                    .setMaxLength(750)
                                    .build();

                                Modal.Builder modal = Modal.create("report:" + guildId + ":" + message.getId(), select.getGuild().getName())
                                    .addActionRows(ActionRow.of(username), ActionRow.of(reason), ActionRow.of(evidence));

                                if (!(guildId.equals("572104809973415943") || guildId.equals("371062894315569173"))) {
                                    modal.addActionRows(ActionRow.of(proofOfWarning));
                                }


                                message.editMessageEmbeds(context.makeInfo("You have been sent a modal, please respond to the questions asked and come back here, if you're not back in 3 minutes. The modal expires").buildEmbed()).setActionRows(Collections.emptySet())
                                    .queue(p -> {
                                        select.replyModal(modal.build()).queue();
                                        avaire.getWaiter().waitForEvent(ModalInteractionEvent.class,
                                            modalInteractionEvent -> modalInteractionEvent.getUser().equals(context.member.getUser()) &&
                                                modalInteractionEvent.getModalId().equals("report:" + guildId + ":" + message.getId()),
                                            act -> {
                                                String modalUsername = act.getInteraction().getValue("username").getAsString();
                                                String modalReason = act.getInteraction().getValue("reason").getAsString();
                                                String modalEvidence = act.getInteraction().getValue("evidence").getAsString();
                                                ModalMapping modalProofOfWarning = act.getInteraction().getValue("proofOfWarning");

                                                //message.delete().queue();
                                                goToStep2(act, modalUsername, modalReason, modalEvidence, modalProofOfWarning, guildId, message);

                                            },
                                            3, TimeUnit.MINUTES, () -> {
                                                select.reply("The modal has expired, please try again.").queue();
                                            });
                                    });

                            /*try {


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
                            }*/
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

    private void goToStep2(ModalInteractionEvent act, String modalUsername, String modalReason, String modalEvidence, ModalMapping modalProofOfWarning, String guildId, Message message) {

        Long requestedId = getRobloxId(modalUsername);
        if (requestedId == 0L) {
            act.getInteraction().reply(modalUsername + " does no exist on roblox.").setEphemeral(true).queue();
            return;
        }

        Guild g = avaire.getShardManager().getGuildById(guildId);
        if (g == null) return;

        GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, g);
        TextChannel tc = avaire.getShardManager().getTextChannelById(settings.getHandbookReportChannel());
        if (tc == null)
            act.getInteraction().reply("The guild doesn't have a (valid) channel for handbook reports.").queue();

        boolean isBlacklisted = checkIfBlacklisted(requestedId, tc);
        if (isBlacklisted) {
            act.getInteraction().reply(modalUsername + ": This user is already blacklisted in `" + tc.getGuild().getName() + "`.").setEphemeral(true).queue();
            return;
        }


        if (settings.getRobloxGroupId() != 0 && settings.getRobloxGroupId() != 159511) {
            Request requestedRequest = RequestFactory.makeGET("https://groups.roblox.com/v1/users/" + requestedId + "/groups/roles");
            requestedRequest.send((Consumer <Response>) response -> {
                if (response.getResponse().code() == 200) {
                    RobloxUserGroupRankService grs = (RobloxUserGroupRankService) response.toService(RobloxUserGroupRankService.class);
                    Optional <RobloxUserGroupRankService.Data> b = grs.getData().stream().filter(group -> group.getGroup().getId() == settings.getRobloxGroupId()).findFirst();

                    if (b.isPresent()) {
                        askConfirmation(modalEvidence, tc, act, modalUsername, modalReason, modalProofOfWarning, b.get().getRole().getName(), message);
                    } else {
                        //context.makeInfo(String.valueOf(response.getResponse().code())).queue();
                        act.getInteraction().reply("The user who you've requested a punishment for isn't in `" + tc.getGuild().getName() + "`, please check if this is correct or not.").queue();

                    }
                } else {
                    act.getInteraction().reply("Something went wrong with the roblox API, please try again later.").setEphemeral(true).queue();
                }
            });
        } else {
            askConfirmation(modalEvidence, tc, act, modalUsername, modalReason, modalProofOfWarning, "Not in group", message);
        }
    }

    private void askConfirmation(String modalEvidence, TextChannel tc, ModalInteractionEvent act, String modalUsername, String modalReason, ModalMapping modalProofOfWarning, String rank, Message message) {
        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setDescription("You are reporting `" + modalUsername + "`\n")
            .addField("Reason", modalReason, false)
            .addField("Evidence", modalEvidence, false)
            .addField("Rank", rank, false)
            .setColor(Color.CYAN);

        if (modalProofOfWarning != null) {
            embedBuilder.addField("Proof of Warning", modalProofOfWarning.getAsString(), false).setColor(Color.RED);
        }

        Button yes = Button.secondary("confirm:" + modalUsername + ":" + act.getUser().getId(), "Yes").withEmoji(Emoji.fromUnicode("✅"));
        Button no = Button.primary("deny:" + modalUsername + ":" + act.getUser().getId(), "No").withEmoji(Emoji.fromUnicode("❌"));

        act.getInteraction().replyEmbeds(
            embedBuilder.build()
        ).addActionRow(yes.asEnabled(), no.asEnabled()).setEphemeral(true).queue(
            ignored -> {
                avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class,
                    event -> event.getChannel().getId().equals(ignored.getInteraction().getMessageChannel().getId())
                    && ignored.getInteraction().getMember().equals(act.getMember()),
                    send -> {
                        if (send.getButton().getEmoji().getName().equalsIgnoreCase("❌") || send.getButton().getEmoji().getName().equalsIgnoreCase("x")) {
                            act.editMessage("Report has been canceled, if you want to restart the report. Do `!ru` in any bot-commands channel.")
                                .setEmbeds(Collections.emptyList())
                                .setActionRows(Collections.emptyList()).queue();
                            return;
                        } else if (send.getButton().getEmoji().getName().equalsIgnoreCase("✅")) {
                            sendReport(tc, send, modalUsername, modalReason, modalProofOfWarning, rank, modalEvidence, message);
                        }

                    },
                    1, TimeUnit.MINUTES,
                    () -> {
                        act.editMessage("You took to long to respond, please restart the report system!")
                            .setEmbeds(Collections.emptyList())
                            .setActionRows(Collections.emptyList()).queue();
                    });
            }
        );
    }


    private void sendReport(TextChannel tc, ButtonInteractionEvent act, String modalUsername, String modalReason, ModalMapping modalProofOfWarning, String rank, String modalEvidence, Message message) {

        Button b1 = Button.success("accept:" + tc.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.danger("reject:" + tc.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
        Button b3 = Button.secondary("remove:" + tc.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));

        tc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(32, 34, 37))
                .setAuthor("Report created for: " + modalUsername, null, getImageByName(tc.getGuild(), modalUsername))
                .setDescription(
                    "**Violator**: " + modalUsername + "\n" +
                        "**Rank**: `" + rank + "`\n" +
                        "**Information**: \n" + modalReason + "\n\n" +
                        "**Evidence**: \n" + modalEvidence +
                        (modalProofOfWarning != null ? "\n\n**Evidence of warning**:\n" + modalProofOfWarning.getAsString() : ""))
                .requestedBy(act.getMember())
                .setTimestamp(Instant.now())
                .buildEmbed()).setActionRow(b1.asEnabled(), b2.asEnabled(), b3.asEnabled())
            .queue(
                finalMessage -> {
                    message.editMessageEmbeds(MessageFactory.makeSuccess(finalMessage, "[Your report has been created in the correct channel.](:link).").set("link", finalMessage.getJumpUrl())
                            .buildEmbed()).setActionRows(Collections.emptyList())
                        .queue();

                    act.editMessage("Please check the previous message for the report.")
                        .setEmbeds(Collections.emptySet())
                        .setActionRows(Collections.emptySet())
                        .queue();

                    createReactions(finalMessage);
                    try {
                        avaire.getDatabase().newQueryBuilder(Constants.REPORTS_DATABASE_TABLE_NAME).insert(data -> {
                            data.set("pb_server_id", finalMessage.getGuild().getId());
                            data.set("report_message_id", finalMessage.getId());
                            data.set("reporter_discord_id", act.getMember().getId());
                            data.set("reporter_discord_name", act.getMember().getEffectiveName(), true);
                            data.set("reported_roblox_id", getRobloxId(modalUsername));
                            data.set("reported_roblox_name", modalUsername);
                            data.set("report_evidence", modalEvidence, true);
                            data.set("report_evidence_warning", modalProofOfWarning != null ? modalProofOfWarning.getAsString() : null, true);
                            data.set("report_reason", modalReason, true);
                            data.set("reported_roblox_rank", rank);
                        });
                    } catch (SQLException throwables) {
                        Xeus.getLogger().error("ERROR: ", throwables);
                    }

                }
            );
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
        } else if (context.message.getMentions().getEmotes().size() == 1) {
            e = context.message.getMentions().getEmotes().get(0);
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
        } else if (context.message.getMentions().getEmotes().size() == 1) {
            e = context.message.getMentions().getEmotes().get(0);
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
                q.set("emoji_id", emote.getId());
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
            JSONObject json = readJsonFromUrl("https://api.roblox.com/users/get-by-username?username=" + un);
            return Double.valueOf(json.getDouble("Id")).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void tookToLong(CommandMessage event) {
        event.makeError("<a:alerta:729735220319748117> You've taken to long to react to the message <a:alerta:729735220319748117>").queue();
    }
}
