/*
 * Copyright (c) 2018.
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

package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.contracts.cache.CacheAdapter;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.handlers.DatabaseEventHolder;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.RestActionUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ButtonClickEventAdapter extends EventAdapter {

    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    /**
     * Instantiates the event adapter and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public ButtonClickEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onReportsButtonInteractionEvent(ButtonInteractionEvent e) {
        loadDatabasePropertiesIntoMemory(e).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuildSettings().getHandbookReportChannel() != 0) {
                TextChannel tc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuildSettings().getHandbookReportChannel());
                if (tc != null) {
                    int permissionLevel = XeusPermissionUtil.getPermissionLevel(databaseEventHolder.getGuildSettings(), e.getGuild(), e.getMember()).getLevel();
                    if (e.getChannel().equals(tc)) {
                        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.REPORTS_DATABASE_TABLE_NAME).where("pb_server_id", e.getGuild().getId()).andWhere("report_message_id", e.getMessageId());
                        try {
                            DataRow c = qb.get().get(0);

                            if (qb.get().size() < 1) {
                                return;
                            }
                            String username = c.getString("reported_roblox_name");
                            String description = c.getString("report_reason");
                            String evidence = c.getString("report_evidence");
                            String warningEvidence = c.getString("report_evidence_warning");
                            long reporter = c.getLong("reporter_discord_id");
                            String rank = c.getString("reported_roblox_rank");
                            long reportedRobloxId = c.getLong("reported_roblox_id");
                            User memberAsReporter = avaire.getShardManager().getUserById(reporter);

                            e.deferEdit().queue(deferReply -> {
                                switch (e.getButton().getEmoji().getName()) {
                                    case "✅":
                                        if (permissionLevel >=
                                            GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
                                            if (e.getGuild().getId().equals("438134543837560832")) {

                                                Long userId = reportedRobloxId;
                                                Long points = avaire.getRobloxAPIManager().getKronosManager().getPoints(reportedRobloxId);

                                                tc.retrieveMessageById(c.getLong("report_message_id")).queue(v -> {
                                                    if (v.getEmbeds().get(0).getColor().equals(new Color(0, 255, 0)))
                                                        return;

                                                    e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(100, 200, 200),
                                                            "You've chosen to approve this report, may I know the amount of points I have to remove? (This user currently has `:points` points)")
                                                        .requestedBy(e.getMember()).set("points", points).buildEmbed()).queue(z -> {
                                                        avaire.getWaiter().waitForEvent(MessageReceivedEvent.class,
                                                            p -> p.getMember().equals(e.getMember()) && e.getChannel().equals(p.getChannel()) && NumberUtil.isNumeric(p.getMessage().getContentStripped()), run -> {
                                                                v.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(0, 255, 0))
                                                                        .setAuthor("Report created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                                        .setDescription(
                                                                            "**Violator**: " + username + "\n" +
                                                                                (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                                                "**Information**: \n" + description + "\n\n" +
                                                                                "**Evidence**: \n" + evidence + "\n\n" +
                                                                                (warningEvidence != null ? "**Evidence of warning**:\n" + warningEvidence + "\n\n" : "") +
                                                                                "**Punishment**: \n``" + run.getMessage().getContentRaw() + "`` points pending removal.")
                                                                        .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                                        .setTimestamp(Instant.now()).set("rRank", rank)
                                                                        .buildEmbed()).setActionRows(Collections.emptyList())
                                                                    .queue();
                                                                try {
                                                                    qb.useAsync(true).update(statement -> {
                                                                        statement.set("report_punishment", run.getMessage().getContentRaw(), true);
                                                                    });
                                                                } catch (SQLException throwables) {
                                                                    e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0))
                                                                        .requestedBy(e.getMember())
                                                                        .setDescription("Something went wrong in the database, please contact the developer.")
                                                                        .setFooter("This message will self-destruct in 30s").buildEmbed()).queue(n -> {
                                                                        n.delete().queueAfter(30, TimeUnit.SECONDS);
                                                                    });
                                                                }
                                                                z.delete().queue();
                                                                v.clearReactions().queue();
                                                                run.getMessage().delete().queue();

                                                                Request.Builder request = new Request.Builder()
                                                                    .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                                                                    .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosDatabaseApiKey"))
                                                                    .url("https://www.pb-kronos.dev/api/v2/smartlog/pbst/single")
                                                                    .post(RequestBody.create(json, buildPayload(username, userId, -Long.parseLong(run.getMessage().getContentRaw()))));

                                                                try (okhttp3.Response exportResponse = client.newCall(request.build()).execute()) {
                                                                    e.getChannel().sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel())
                                                                        .requestedBy(e.getMember()).setDescription("Sent point export to the database, please use ``;smartlogs`` in a bot commands channel to update the smartlog that was just sent to Kronos. Debugging info: \n```json\n" +
                                                                            ":info```").set("info", exportResponse.body() != null ? exportResponse.body().string() : "Empty Body").setFooter("This message self-destructs after 25 seconds").buildEmbed()).queue(b -> {
                                                                        b.delete().queueAfter(25, TimeUnit.SECONDS);
                                                                    });
                                                                } catch (IOException error) {
                                                                    Xeus.getLogger().error("Failed sending sync with beacon request: " + error.getMessage());
                                                                }
                                                            });
                                                    });
                                                });


                                            } else {
                                                tc.retrieveMessageById(c.getLong("report_message_id")).queue(v -> {
                                                    if (v.getEmbeds().get(0).getColor().equals(new Color(0, 255, 0)))
                                                        return;

                                                    e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(
                                                        MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(100, 200, 200),
                                                                "You've chosen to approve a report, may I know the punishment you're giving to the user?")
                                                            .requestedBy(e.getMember()).buildEmbed()).queue(z -> {
                                                        avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, p -> {
                                                            return p.getMember() != null && p.getMember().equals(e.getMember()) && e.getChannel().equals(p.getChannel());
                                                        }, run -> {
                                                            v.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(0, 255, 0))
                                                                    .setAuthor("Report created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                                    .setDescription(
                                                                        "**Violator**: " + username + "\n" +
                                                                            (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                                            "**Information**: \n" + description + "\n\n" +
                                                                            "**Evidence**: \n" + evidence + "\n\n" +
                                                                            (warningEvidence != null ? "**Evidence of warning**:\n" + warningEvidence + "\n\n" : "") +
                                                                            "**Punishment**: \n" + run.getMessage().getContentRaw())
                                                                    .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                                    .setTimestamp(Instant.now()).set("rRank", rank)
                                                                    .buildEmbed()).setActionRows(Collections.emptyList())
                                                                .queue();
                                                            try {
                                                                qb.useAsync(true).update(statement -> {
                                                                    statement.set("report_punishment", run.getMessage().getContentRaw(), true);
                                                                });
                                                            } catch (SQLException throwables) {
                                                                e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).requestedBy(e.getMember()).setDescription("Something went wrong in the database, please contact the developer.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(n -> {
                                                                    n.delete().queueAfter(30, TimeUnit.SECONDS);
                                                                });
                                                            }
                                                            z.delete().queue();
                                                            v.clearReactions().queue();
                                                            run.getMessage().delete().queue();
                                                        });
                                                    });
                                                });
                                            }
                                        } else {
                                            deferReply.setEphemeral(true).sendMessage("Sorry, but you're not allowed to approve this report. You have to be at least a **Manager** to do this.").queue();
                                        }
                                        break;
                                    case "❌":
                                        if (permissionLevel >=
                                            GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {

                                            tc.retrieveMessageById(c.getLong("report_message_id")).queue(v -> {
                                                if (v.getEmbeds().get(0).getColor().equals(new Color(255, 0, 0)))
                                                    return;

                                                e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(100, 200, 200), "You've chosen to reject a report, may I know the reason you're giving for this?").requestedBy(e.getMember()).buildEmbed()).queue(z -> {
                                                    avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, p -> {
                                                        return p.getMember().equals(e.getMember()) && e.getChannel().equals(p.getChannel());
                                                    }, run -> {
                                                        v.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(255, 0, 0))
                                                                .setAuthor("Report created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                                .setDescription(
                                                                    "**Violator**: " + username + "\n" +
                                                                        (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                                        "**Information**: \n" + description + "\n\n" +
                                                                        "**Evidence**: \n" + evidence + "\n\n" +
                                                                        (warningEvidence != null ? "**Evidence of warning**:\n" + warningEvidence + "\n\n" : "") +
                                                                        "**Denial Reason**: \n" + run.getMessage().getContentRaw())
                                                                .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                                .setTimestamp(Instant.now()).set("rRank", rank)
                                                                .buildEmbed()).setActionRows(Collections.emptyList())
                                                            .queue();
                                                        v.clearReactions().queue();
                                                        try {
                                                            qb.useAsync(true).delete();
                                                        } catch (SQLException throwables) {
                                                            e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Something went wrong in the database, please contact the developer.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(n -> {
                                                                n.delete().queueAfter(30, TimeUnit.SECONDS);
                                                            });
                                                        }
                                                        z.delete().queue();
                                                        v.clearReactions().queue();
                                                        run.getMessage().delete().queue();
                                                    });
                                                });
                                            });
                                        } else {
                                            deferReply.setEphemeral(true).sendMessage("Sorry, but you're not allowed to reject this report. You have to be at least a **Manager** to do this.").queue();
                                        }
                                        break;
                                    case "🚫":
                                        if (permissionLevel >=
                                            GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {


                                            tc.retrieveMessageById(c.getLong("report_message_id")).queue(v -> {
                                                v.delete().queue();
                                            });
                                            try {
                                                qb.useAsync(true).delete();
                                            } catch (SQLException throwables) {
                                                Xeus.getLogger().error("ERROR: ", throwables);
                                            }


                                        } else {
                                            deferReply.setEphemeral(true).sendMessage("Sorry, but you're not allowed to delete this report. You have to be at least a **Manager** to do this.").queue();
                                        }
                                }
                            });
                        } catch (SQLException throwables) {
                            Xeus.getLogger().error("ERROR: ", throwables);
                        }

                    }
                }
            }
        });
    }

    public void onPatrolRemittanceButtonInteractionEvent(ButtonInteractionEvent e) {
        loadDatabasePropertiesIntoMemory(e).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuildSettings().getPatrolRemittanceChannel() != 0) {

                TextChannel tc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuildSettings().getPatrolRemittanceChannel());
                if (tc != null) {

                    int permissionLevel = XeusPermissionUtil.getPermissionLevel(databaseEventHolder.getGuildSettings(), e.getGuild(), e.getMember()).getLevel();
                    if (e.getChannel().equals(tc)) {

                        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.REMITTANCE_DATABASE_TABLE_NAME)
                            .where("pb_server_id", e.getGuild().getId()).andWhere("request_message_id", e.getMessageId());
                        try {
                            DataRow c = qb.get().get(0);
                            if (qb.get().size() < 1) {
                                return;
                            }

                            String username = c.getString("requester_discord_name");
                            String evidence = c.getString("requester_evidence");
                            long requester = c.getLong("requester_discord_id");
                            String rank = c.getString("requester_roblox_rank");
                            User memberAsReporter = avaire.getShardManager().getUserById(requester);

                            e.deferEdit().queue(deferEdit -> {
                                switch (e.getButton().getEmoji().getName()) {
                                    case "✅":
                                        if (permissionLevel >=
                                            GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {

                                            if (e.getGuild().getId().equals("438134543837560832")) {
                                                tc.retrieveMessageById(c.getLong("request_message_id")).queue(v -> {
                                                    if (v.getEmbeds().get(0).getColor().equals(new Color(0, 255, 0)))
                                                        return;

                                                    e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(100, 200, 200), "You've chosen to approve a remittance, how many points you want to give to the user?").buildEmbed()).queue(z -> {
                                                        avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, p -> {
                                                            return p.getMember().equals(e.getMember()) && e.getChannel().equals(p.getChannel()) && NumberUtil.isNumeric(p.getMessage().getContentRaw());
                                                        }, run -> {
                                                            v.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(0, 255, 0))
                                                                    .setAuthor("Remittance created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                                    .setDescription(
                                                                        "**Username**: " + username + "\n" +
                                                                            (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                                            "**Evidence**: \n" + evidence +
                                                                            "\n**Points awarded**: \n" + run.getMessage().getContentRaw())
                                                                    .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                                    .setTimestamp(Instant.now()).set("rRank", rank)
                                                                    .buildEmbed()).setActionRows(Collections.emptyList())
                                                                .queue();

                                                            Request.Builder request = new Request.Builder()
                                                                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                                                                .addHeader("Access-Key", avaire.getConfig().getString("apiKeys.kronosDatabaseApiKey"))
                                                                .url("https://www.pb-kronos.dev/api/v2/smartlog/pbst/single")
                                                                .post(RequestBody.create(json, buildPayload(username, avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(username), Long.valueOf(run.getMessage().getContentRaw()))));

                                                            try (okhttp3.Response exportResponse = client.newCall(request.build()).execute()) {
                                                                e.getChannel().sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel())
                                                                    .setDescription("Sent point export to the database, please use ``;smartlogs`` in a bot commands channel to update the smartlog that was just sent to Kronos. Debugging info: \n```json\n" +
                                                                        ":info```").set("info", exportResponse.body() != null ? exportResponse.body().string() : "Empty Body").setFooter("This message self-destructs after 25 seconds").buildEmbed()).queue(b -> {
                                                                    b.delete().queueAfter(25, TimeUnit.SECONDS);
                                                                });
                                                            } catch (IOException error) {
                                                                Xeus.getLogger().error("Failed sending sync with beacon request: " + error.getMessage());
                                                            }

                                                            try {
                                                                qb.useAsync(true).update(statement -> {
                                                                    statement.set("action", run.getMessage().getContentRaw(), true);
                                                                });
                                                            } catch (SQLException throwables) {
                                                                e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Something went wrong in the database, please contact the developer.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(n -> {
                                                                    n.delete().queueAfter(30, TimeUnit.SECONDS);
                                                                });
                                                            }
                                                            z.delete().queue();
                                                            v.clearReactions().queue();
                                                            run.getMessage().delete().queue();
                                                        });
                                                    });
                                                });
                                            } else {
                                                tc.retrieveMessageById(c.getLong("request_message_id")).queue(v -> {
                                                    if (v.getEmbeds().get(0).getColor().equals(new Color(0, 255, 0)))
                                                        return;

                                                    e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(100, 200, 200), "You've chosen to approve a remittance request, may I know the reward you're giving to the user?").buildEmbed()).queue(z -> {
                                                        avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, p -> {
                                                            return p.getMember().equals(e.getMember()) && e.getChannel().equals(p.getChannel());
                                                        }, run -> {
                                                            v.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(0, 255, 0))
                                                                    .setAuthor("Remittance created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                                    .setDescription(
                                                                        "**Username**: " + username + "\n" +
                                                                            (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                                            "**Evidence**: \n" + evidence +
                                                                            "\n**Reward/Acceptal Reason**: \n" + run.getMessage().getContentRaw())
                                                                    .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                                    .setTimestamp(Instant.now()).set("rRank", rank)
                                                                    .buildEmbed()).setActionRows(Collections.emptyList())
                                                                .queue();
                                                            try {
                                                                qb.useAsync(true).update(statement -> {
                                                                    statement.set("action", run.getMessage().getContentRaw(), true);
                                                                });
                                                            } catch (SQLException throwables) {
                                                                e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Something went wrong in the database, please contact the developer.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(n -> {
                                                                    n.delete().queueAfter(30, TimeUnit.SECONDS);
                                                                });
                                                            }
                                                            z.delete().queue();
                                                            v.clearReactions().queue();
                                                            run.getMessage().delete().queue();
                                                        });
                                                    });
                                                });
                                            }
                                        } else {
                                            deferEdit.setEphemeral(true).sendMessage("Sorry, but you do not have the required permissions to approve this remittance.").queue();
                                        }
                                        break;
                                    case "❌":
                                        if (permissionLevel >=
                                            GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
                                            //e.getReaction().removeReaction(e.getUser()).queue();


                                            tc.retrieveMessageById(c.getLong("request_message_id")).queue(v -> {
                                                if (v.getEmbeds().get(0).getColor().equals(new Color(255, 0, 0)))
                                                    return;

                                                e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(100, 200, 200), "You've chosen to reject a report, may I know the reason you're giving for this?").buildEmbed()).queue(z -> {
                                                    avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, p -> {
                                                        return p.getMember() != null && p.getMember().equals(e.getMember()) && e.getChannel().equals(p.getChannel());
                                                    }, run -> {
                                                        v.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(255, 0, 0))
                                                                .setAuthor("Report created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                                .setDescription(
                                                                    "**Username**: " + username + "\n" +
                                                                        (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                                        "**Evidence**: \n" + evidence +
                                                                        "\n**Denial Reason**: \n" + run.getMessage().getContentRaw())
                                                                .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                                .setTimestamp(Instant.now()).set("rRank", rank)
                                                                .buildEmbed())
                                                            .setActionRows(Collections.emptyList())
                                                            .queue();
                                                        try {
                                                            qb.useAsync(true).delete();
                                                        } catch (SQLException throwables) {
                                                            e.getChannel().sendMessage(e.getMember().getAsMention()).setEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Something went wrong in the database, please contact the developer.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(n -> {
                                                                n.delete().queueAfter(30, TimeUnit.SECONDS);
                                                            });
                                                        }
                                                        z.delete().queue();
                                                        run.getMessage().delete().queue();
                                                    });
                                                });
                                            });
                                        } else {
                                            deferEdit.setEphemeral(true).sendMessage("Sorry, but you do not have the required permissions to reject this remittance.").queue();
                                        }
                                        break;
                                    case "🚫":
                                        if (permissionLevel >=
                                            GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {


                                            tc.retrieveMessageById(c.getLong("request_message_id")).queue(v -> {
                                                v.delete().queue();
                                            });
                                            try {
                                                qb.useAsync(true).delete();
                                            } catch (SQLException throwables) {
                                                Xeus.getLogger().error("ERROR: ", throwables);
                                            }


                                        } else {
                                            deferEdit.setEphemeral(true).sendMessage("Sorry, but you do not have the required permissions to remove this remittance.").queue();
                                        }
                                }
                            });
                        } catch (SQLException throwables) {
                            Xeus.getLogger().error("ERROR: ", throwables);
                        }


                    }
                }

            }
        });
    }

    public void onFeedbackButtonInteractionEvent(ButtonInteractionEvent e) {
        if (e.getMember().getUser().isBot()) {
            return;
        }
        loadDatabasePropertiesIntoMemory(e).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuildSettings().getSuggestionChannelId() != 0 || databaseEventHolder.getGuildSettings().getSuggestionCommunityChannelId() != 0) {
                try {
                    QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.PB_SUGGESTIONS_TABLE_NAME).where("pb_server_id", e.getGuild().getId()).andWhere("suggestion_message_id", e.getMessageId());

                    TextChannel tc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuildSettings().getSuggestionChannelId());
                    TextChannel ctc = null;
                    if (databaseEventHolder.getGuildSettings().getSuggestionCommunityChannelId() != 0) {
                        if (avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuildSettings().getSuggestionCommunityChannelId()) != null) {
                            ctc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuildSettings().getSuggestionCommunityChannelId());
                        }
                    }

                    String id = null;
                    if (qb.get().size() > 0) {
                        id = qb.get().get(0).getString("suggester_discord_id");
                    }

                    Member memberCheck = null;
                    if (id != null) {
                        memberCheck = e.getGuild().getMemberById(id);
                    }

                    Member m = memberCheck != null ? memberCheck : e.getMember();

                    if (tc != null) {
                        if (!(tc.equals(e.getChannel()) || ctc.equals(e.getChannel()))) {
                            return;
                        }
                        TextChannel finalCtc = ctc;
                        e.getChannel().retrieveMessageById(e.getMessageId()).queue(msg -> {
                            e.deferEdit().queue(deferReply -> {
                                try {

                                    if (e.getButton().getEmoji().getName().equals("❌") || e.getButton().getEmoji().getName().equals("✅") || e.getButton().getEmoji().getName().equals("\uD83D\uDD04")) {
                                        switch (e.getButton().getEmoji().getName()) {
                                            case "❌" -> {
                                                if (!(isValidReportManager(e, 2))) {
                                                    deferReply.setEphemeral(true).sendMessage("Sorry, but you have to be a **Manager** or above to reject an report.").queue();
                                                    return;
                                                }
                                                msg.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0))
                                                    .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Denied by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                    .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                    .setDescription(msg.getEmbeds().get(0).getDescription())
                                                    .setTimestamp(Instant.now())
                                                    .buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                                msg.clearReactions().queue();
                                                qb.delete();
                                                if (msg.getStartedThread() != null && !msg.getStartedThread().isArchived() && !msg.getStartedThread().isLocked()) {
                                                    msg.getStartedThread().getManager().setArchived(true).queue();
                                                }
                                            }
                                            case "✅" -> {
                                                if (!(isValidReportManager(e, 2))) {
                                                    deferReply.setEphemeral(true).sendMessage("Sorry, but you have to be a **Manager** or above to accept a report.").queue();
                                                    return;
                                                }
                                                if (databaseEventHolder.getGuildSettings().getSuggestionApprovedChannelId() != 0) {
                                                    TextChannel atc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuildSettings().getSuggestionApprovedChannelId());
                                                    if (atc != null) {
                                                        atc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(0, 255, 0))
                                                            .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Approved by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                            .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                            .setDescription(msg.getEmbeds().get(0).getDescription())
                                                            .setTimestamp(Instant.now())
                                                            .buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                                        msg.delete().queue();
                                                    } else {
                                                        msg.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(0, 255, 0))
                                                            .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Approved by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                            .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                            .setDescription(msg.getEmbeds().get(0).getDescription())
                                                            .setTimestamp(Instant.now())
                                                            .buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                                        msg.clearReactions().queue();

                                                    }
                                                } else {
                                                    msg.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(0, 255, 0))
                                                        .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Approved by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                        .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                        .setDescription(msg.getEmbeds().get(0).getDescription())
                                                        .setTimestamp(Instant.now())
                                                        .buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                                    msg.clearReactions().queue();

                                                }
                                                try {
                                                    qb.delete();
                                                    if (msg.getStartedThread() != null && !msg.getStartedThread().isArchived() && !msg.getStartedThread().isLocked()) {
                                                        msg.getStartedThread().getManager().setArchived(true).queue();
                                                    }
                                                } catch (SQLException throwables) {
                                                    Xeus.getLogger().error("ERROR: ", throwables);
                                                }
                                            }
                                            case "\uD83D\uDD04" -> { // 🔄
                                                if (!(isValidReportManager(e, 2))) {
                                                    deferReply.setEphemeral(true).sendMessage("Sorry, but you have to be a **Manager** or above to reject an report.").queue();

                                                    return;
                                                }
                                                msg.clearReactions().queue();
                                                msg.addReaction("\uD83D\uDC4D").queue(); //
                                                msg.addReaction("\uD83D\uDC4E").queue(); // 👎
                                            }
                                        }
                                    }
                                    if (e.getButton().getEmoji().getName().equals("\uD83D\uDEAB")) { //🚫
                                        if (!(isValidReportManager(e, 1))) {
                                            deferReply.setEphemeral(true).sendMessage("Sorry, but you have to be a **Manager** or above to delete an report.").queue();
                                            return;
                                        }
                                        if (msg.getStartedThread() != null) {
                                            msg.getStartedThread().delete().queue();
                                        }
                                        msg.delete().queue();
                                    } //🚫
                                    if (e.getButton().getEmoji().getName().equals("\uD83D\uDCAC")) {
                                        if (!(e.getMember().hasPermission(Permission.MESSAGE_MANAGE) || isValidReportManager(e, 1))) {
                                            deferReply.setEphemeral(true).sendMessage("Sorry, but you have to be a **Manager** or above to comment this report.").queue();
                                            return;
                                        }

                                        if (isValidReportManager(e, 1)) {
                                            msg.getChannel().sendMessage(e.getMember().getAsMention() + "\nWhat is your comment?").queue(
                                                v -> avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, c -> c.getChannel().equals(e.getChannel()) && c.getMember().equals(e.getMember()), c -> {
                                                    v.delete().queue();
                                                    msg.editMessageEmbeds(new EmbedBuilder()
                                                            .setColor(msg.getEmbeds().get(0).getColor())
                                                            .setAuthor("Suggestion for: " + e.getGuild().getName(), null, e.getGuild().getIconUrl())
                                                            .setDescription(msg.getEmbeds().get(0).getDescription() + "\n\n" + getRole(c) + " - :speech_balloon: **``" + e.getMember().getEffectiveName() + "``:**\n" + c.getMessage().getContentRaw())
                                                            .setTimestamp(msg.getEmbeds().get(0).getTimestamp())
                                                            .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl()).build())
                                                        .queue();
                                                    c.getMessage().delete().queue();
                                                    if (e.getGuild().getMembersByEffectiveName(msg.getEmbeds().get(0).getFooter().getText(), true).size() > 0) {
                                                        for (Member u : e.getGuild().getMembersByEffectiveName(msg.getEmbeds().get(0).getFooter().getText(), true)) {
                                                            u.getUser().openPrivateChannel().complete()
                                                                .sendMessageEmbeds(new EmbedBuilder()
                                                                    .setDescription("Hello there ``" + u.getEffectiveName() + "``.\n" +
                                                                        "It seems like you have gotten a comment on one of your suggestions!\n" +
                                                                        "If you want to check the feedback, [click here](" + msg.getJumpUrl() + ")\n" +
                                                                        "You received a comment from **" + e.getMember().getEffectiveName() + "** in ``" + e.getGuild().getName() + "``!\n\n" +
                                                                        "**Comment**:\n" + c.getMessage().getContentRaw()).build()).queue();
                                                        }
                                                    }
                                                }, 90, TimeUnit.SECONDS, () -> {
                                                    v.delete().queue();
                                                    msg.getMember().getUser().openPrivateChannel().queue(l -> l.sendMessage("You took to long to send a comment, please re-react to the message!").queue());
                                                })
                                            );
                                        }
                                    } //💬
                                    if (e.getButton().getEmoji().getName().equals("\uD83D\uDC51")) {
                                        if (!(isValidReportManager(e, 1))) {
                                            deferReply.setEphemeral(true).sendMessage("Sorry, but you have to be a **Local Moderator** or above to move a report to CAS.").queue();
                                            return;
                                        }

                                        if (finalCtc != null) {
                                            PlaceholderMessage mb = MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 100, 0))
                                                .setAuthor("Suggestion for: " + e.getGuild().getName(), null, e.getGuild().getIconUrl())
                                                .setDescription(msg.getEmbeds().get(0).getDescription())
                                                .setTimestamp(Instant.now());


                                            if (qb.get().size() < 1) {
                                                mb.setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl());
                                            } else {
                                                mb.requestedBy(m);
                                            }

                                            Button b1 = Button.success("accept:" + finalCtc.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
                                            Button b2 = Button.danger("reject:" + finalCtc.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
                                            Button b3 = Button.secondary("remove:" + finalCtc.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));
                                            Button b4 = Button.secondary("comment:" + finalCtc.getId(), "Comment").withEmoji(Emoji.fromUnicode("\uD83D\uDCAC"));
                                            Button b5 = Button.secondary("community-move:" + finalCtc.getId(), "Move to CAS").withEmoji(Emoji.fromUnicode("\uD83D\uDC51"));

                                            finalCtc.sendMessageEmbeds(mb.buildEmbed()).setActionRow(b1.asEnabled(), b2.asEnabled(), b3.asEnabled(), b4.asEnabled(), b5.asDisabled()).queue(p -> {
                                                try {
                                                    qb.update(l -> {
                                                        l.set("suggestion_message_id", p.getId());
                                                    });
                                                } catch (SQLException throwables) {
                                                    Xeus.getLogger().error("ERROR: ", throwables);
                                                }
                                            });

                                            msg.delete().queue();
                                        }

                                    } //👑

                                } catch (SQLException throwables) {
                                    Xeus.getLogger().error("ERROR: ", throwables);
                                }
                            });
                        });
                    }
                } catch (SQLException throwables) {
                    Xeus.getLogger().error("ERROR: ", throwables);
                }
            }
        });
    }

    public void onQuizButtonInteractionEvent(ButtonInteractionEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuildSettings().getEvaluationEvalChannel() == 0) {
                return;
            }
            if (event.getChannel().getIdLong() != databaseEventHolder.getGuildSettings().getEvaluationEvalChannel()) {
                return;
            }

            event.deferEdit().queue(l -> {
                switch (event.getButton().getEmoji().getName()) {
                    case "\uD83D\uDC4D":
                        startAcceptedEval(event, l);
                        break;
                    case "⛔":
                        startRejectedEval(event, l);
                        break;
                }
            });
        });
    }

    private void startAcceptedEval(ButtonInteractionEvent event, InteractionHook l) {
        event.getMessage().addReaction("\uD83D\uDC4D").queue();
        RobloxAPIManager manager = avaire.getRobloxAPIManager();
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("message_id", event.getMessageId()).get();
            if (c.isEmpty()) {
                l.setEphemeral(true).sendMessage("Sorry, but this quiz has been made invalid. Please contact the dev why this happened.").queue();
                return;
            }

            c.forEach(eval -> {
                Long userId = eval.getLong("roblox_id");
                VerificationEntity ve = manager.getVerification().callDiscordUserFromDatabaseAPI(userId);
                if (ve != null) {
                    User u = avaire.getShardManager().getUserById(ve.getDiscordId());
                    if (u != null) {
                        u.openPrivateChannel()
                            .flatMap(pc -> pc.sendMessageEmbeds(MessageFactory.makeSuccess(
                                event.getMessage(), "Hey there `" + ve.getRobloxUsername() + "`,\n" +
                                    "you have taken the quiz evaluation, and an SD (or higher) has looked into your answers.\n" +
                                    "I'm happy to report you passed the evaluation!").buildEmbed()))
                            .flatMap(ignored -> event.getChannel().sendMessage("Message was sent.")).queue(message -> message.delete().queueAfter(30, TimeUnit.SECONDS), RestActionUtil.ignore);
                    }
                }

                QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", userId);
                String username = manager.getUserAPI().getUsername(userId);

                try {
                    if (qb.get().isEmpty()) {
                        qb.insert(statement -> {
                            statement.set("roblox_username", username).set("passed_quiz", true).set("roblox_id", userId).set("evaluator", event.getMember().getEffectiveName());
                        });
                    } else {
                        qb.update(statement -> {
                            statement.set("roblox_username", username).set("passed_quiz", true).set("evaluator", event.getMember().getEffectiveName());
                        });
                    }
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
                try {
                    avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("roblox_id", userId).delete();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
                event.getMessage().editMessageEmbeds(event.getMessage().getEmbeds()).setActionRows(Collections.emptyList()).queue();
                event.getChannel().sendMessageEmbeds(MessageFactory.makeSuccess(event.getMessage(), "Eval has been accepted, record has been updated in the database!").buildEmbed()).queue(message -> message.delete().queueAfter(30, TimeUnit.SECONDS));

                avaire.getShardManager().getTextChannelById("980947919022731315").sendMessageEmbeds(MessageFactory.makeSuccess(event.getMessage(), "`" + username + "` has passed the `quiz` evaluation.").requestedBy(event.getMember()).buildEmbed()).queue();
                if (avaire.getRobloxAPIManager().getEvaluationManager().getEvaluationStatus(userId).isPassed()) {
                    avaire.getShardManager().getTextChannelById("980947919022731315").sendMessageEmbeds(MessageFactory.makeSuccess(event.getMessage(), "`" + username + "` has now passed all evaluations!").setColor(new Color(255, 215, 0)).buildEmbed()).queue();
                }
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    private void startRejectedEval(ButtonInteractionEvent event, InteractionHook l) {
        event.getMessage().addReaction("⛔").queue();
        RobloxAPIManager manager = avaire.getRobloxAPIManager();
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("message_id", event.getMessageId()).get();
            if (c.isEmpty()) {
                l.setEphemeral(true).sendMessage("Sorry, but this quiz has been made invalid. Please contact the dev why this happened.").queue();
                return;
            }

            c.forEach(eval -> {
                Long userId = eval.getLong("roblox_id");
                VerificationEntity ve = manager.getVerification().callDiscordUserFromDatabaseAPI(userId);
                if (ve != null) {
                    User u = avaire.getShardManager().getUserById(ve.getDiscordId());
                    if (u != null) {
                        u.openPrivateChannel()
                            .flatMap(pc -> pc.sendMessageEmbeds(MessageFactory.makeError(
                                event.getMessage(), "Hey there `" + ve.getRobloxUsername() + "`,\n" +
                                    "you have taken the quiz evaluation, and an SD (or higher) has looked into your answers.\n" +
                                    "I'm sad to tell you that you've failed the evaluation :(").buildEmbed()))
                            .flatMap(ignored -> event.getChannel().sendMessage("Message was sent.")).queue(message -> message.delete().queueAfter(30, TimeUnit.SECONDS), RestActionUtil.ignore);
                    }
                }

                try {
                    avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("roblox_id", userId).delete();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
                event.getMessage().editMessageEmbeds(event.getMessage().getEmbeds()).setActionRows(Collections.emptyList()).queue();
                event.getChannel().sendMessageEmbeds(MessageFactory.makeError(event.getMessage(), "Eval has been rejected, record has been updated in the database!").buildEmbed()).queue(message -> message.delete().queueAfter(30, TimeUnit.SECONDS));
                CacheAdapter cache = Xeus.getInstance().getRobloxAPIManager().getEvaluationManager().getCooldownCache();
                cache.put("evaluation." + userId + ".cooldown", true, 60 * 60 * 48);
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    private CompletableFuture<DatabaseEventHolder> loadDatabasePropertiesIntoMemory(final ButtonInteractionEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            if (!event.getChannel().getType().isGuild()) {
                return new DatabaseEventHolder(null, null, null, null);
            }

            GuildTransformer guild = GuildController.fetchGuild(avaire, event.getGuild());

            if (guild == null || !guild.isLevels() || event.getMember().getUser().isBot()) {
                return new DatabaseEventHolder(guild, null, null, GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild()));
            }
            return new DatabaseEventHolder(guild, null, null, GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild()));
        });
    }

    private String getImageByName(Guild guild, String username) {
        List<Member> members = guild.getMembersByEffectiveName(username, true);

        if (members.size() < 1) return null;
        if (members.size() > 1) return null;
        else return members.get(0).getUser().getEffectiveAvatarUrl();
    }

    private String buildPayload(String username, Long userId, Long points) {
        JSONObject main = new JSONObject();
        JSONArray pointExports = new JSONArray();

        JSONObject data = new JSONObject();
        data.put("Name", username);
        data.put("UserId", userId);
        data.put("Points", points);

        pointExports.put(data);
        main.put("Data", pointExports);


        return main.toString();
    }

    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidReportManager(ButtonInteractionEvent e, Integer i) {
        GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, e.getGuild());
        if (i == 1) {
            return XeusPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getMember()).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_HR.getLevel();
        }
        if (i == 2) {
            return XeusPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getMember()).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel();
        }
        if (i == 3) {
            return XeusPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getMember()).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel();
        }
        return false;
    }

    private String getRole(MessageReceivedEvent c) {
        return getString(c.getMember());
    }

    @NotNull
    private String getString(Member member) {
        return member.getRoles().size() > 0 ? member.getRoles().get(0).getAsMention() : "";
    }
}
