/*
 * Copyright (c) 2018.
 *
 * This file is part of AvaIre.
 *
 * AvaIre is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AvaIre is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AvaIre.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.avairebot.handlers.adapter;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.chat.PlaceholderMessage;
import com.avairebot.contracts.handlers.EventAdapter;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.collection.DataRow;
import com.avairebot.database.controllers.GuildController;
import com.avairebot.database.controllers.ReactionController;
import com.avairebot.database.query.QueryBuilder;
import com.avairebot.database.transformers.GuildTransformer;
import com.avairebot.database.transformers.ReactionTransformer;
import com.avairebot.factories.MessageFactory;
import com.avairebot.handlers.DatabaseEventHolder;
import com.avairebot.scheduler.tasks.DrainReactionRoleQueueTask;
import com.avairebot.utilities.CheckPermissionUtil;
import com.avairebot.utilities.RoleUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.emote.EmoteRemovedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ReactionEmoteEventAdapter extends EventAdapter {

    public ReactionEmoteEventAdapter(AvaIre avaire) {
        super(avaire);
    }

    public static void createReactions(Message r) {
        r.addReaction("\uD83D\uDC4D").queue();   // 👍
        r.addReaction("\uD83D\uDC4E").queue();  // 👎
        r.addReaction("✅").queue();
        r.addReaction("❌").queue();
        r.addReaction("🚫").queue();
        r.addReaction("\uD83D\uDD04").queue(); // 🔄
    }

    public void onEmoteRemoved(EmoteRemovedEvent event) {
        Collection collection = ReactionController.fetchReactions(avaire, event.getGuild());
        if (collection == null || collection.isEmpty()) {
            return;
        }

        boolean wasActionTaken = false;
        for (DataRow row : collection) {
            ReactionTransformer transformer = new ReactionTransformer(row);

            if (transformer.removeReaction(event.getEmote())) {
                try {
                    QueryBuilder query = avaire.getDatabase().newQueryBuilder(Constants.REACTION_ROLES_TABLE_NAME)
                        .useAsync(true)
                        .where("guild_id", transformer.getGuildId())
                        .where("message_id", transformer.getMessageId());

                    if (transformer.getRoles().isEmpty()) {
                        query.delete();
                    } else {
                        query.update(statement -> {
                            statement.set("roles", AvaIre.gson.toJson(transformer.getRoles()));
                        });
                    }

                    wasActionTaken = true;
                } catch (SQLException ignored) {
                    // Since the query is running asynchronously the error will never
                    // actually be catched here since the database thread running
                    // the query will log the error instead.
                }
            }
        }

        if (wasActionTaken) {
            ReactionController.forgetCache(event.getGuild().getIdLong());
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getReactionEmote().isEmote()) {

            ReactionTransformer transformer = getReactionTransformerFromMessageIdAndCheckPermissions(
                event.getGuild(), event.getMessageId(), event.getReactionEmote().getEmote().getIdLong()
            );

            if (transformer == null) {
                return;
            }

            Role role = event.getGuild().getRoleById(transformer.getRoleIdFromEmote(event.getReactionEmote().getEmote()));
            if (role == null) {
                return;
            }

            if (RoleUtil.hasRole(event.getMember(), role) || !event.getGuild().getSelfMember().canInteract(role)) {
                return;
            }

            DrainReactionRoleQueueTask.queueReactionActionEntity(new DrainReactionRoleQueueTask.ReactionActionEntity(
                event.getGuild().getIdLong(),
                event.getMember().getUser().getIdLong(),
                role.getIdLong(),
                DrainReactionRoleQueueTask.ReactionActionType.ADD
            ));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getReactionEmote().isEmote()) {
            ReactionTransformer transformer = getReactionTransformerFromMessageIdAndCheckPermissions(
                event.getGuild(), event.getMessageId(), event.getReactionEmote().getEmote().getIdLong()
            );

            if (transformer == null) {
                return;
            }

            Role role = event.getGuild().getRoleById(transformer.getRoleIdFromEmote(event.getReactionEmote().getEmote()));
            if (role == null) {
                return;
            }

            if (!RoleUtil.hasRole(event.getMember(), role) || !event.getGuild().getSelfMember().canInteract(role)) {
                return;
            }

            DrainReactionRoleQueueTask.queueReactionActionEntity(new DrainReactionRoleQueueTask.ReactionActionEntity(
                event.getGuild().getIdLong(),
                event.getMember().getUser().getIdLong(),
                role.getIdLong(),
                DrainReactionRoleQueueTask.ReactionActionType.REMOVE
            ));
        }
    }

    private ReactionTransformer getReactionTransformerFromMessageIdAndCheckPermissions(@Nonnull Guild guild, @Nonnull String messageId, long emoteId) {
        if (!hasPermission(guild)) {
            return null;
        }

        Collection collection = ReactionController.fetchReactions(avaire, guild);
        if (collection == null || collection.isEmpty()) {
            return null;
        }

        ReactionTransformer transformer = getReactionTransformerFromId(collection, messageId);
        if (transformer == null || !transformer.getRoles().containsKey(emoteId)) {
            return null;
        }
        return transformer;
    }

    private boolean hasPermission(Guild guild) {
        return guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)
            || guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES);
    }

    @Nullable
    private ReactionTransformer getReactionTransformerFromId(@Nonnull Collection collection, @Nonnull String messageId) {
        List<DataRow> messages = collection.where("message_id", messageId);
        if (messages.isEmpty()) {
            return null;
        }
        return new ReactionTransformer(messages.get(0));
    }

    /**
     * Some custom stuff, related to Pinewood specifically.
     */

    public void onGuildSuggestionValidation(GuildMessageReactionAddEvent e) {
        loadDatabasePropertiesIntoMemory(e).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuild().getVoteValidationChannel() != null) {
                if (e.getChannel().getId().equals(databaseEventHolder.getGuild().getVoteValidationChannel())) {
                    e.getChannel().retrieveMessageById(e.getMessageId()).queue(
                        message -> {
                            if (message.getEmbeds().size() > 0) {
                                try {
                                    if (e.getReactionEmote().getName().equals("✅")) {
                                        message.editmessageEmbeds(MessageFactory.makeEmbeddedMessage(message.getTextChannel(), new Color(0, 255, 0))
                                            .setAuthor(message.getEmbeds().get(0).getAuthor().getName(), null, message.getEmbeds().get(0).getAuthor().getIconUrl())
                                            .setDescription(message.getEmbeds().get(0).getDescription())
                                            .setFooter(message.getEmbeds().get(0).getFooter().getText() + " | Accepted by: " + e.getMember().getEffectiveName())
                                            .setTimestamp(Instant.now()).buildEmbed()).queue();

                                        Collection c = avaire.getDatabase().newQueryBuilder(Constants.MOTS_VOTE_TABLE_NAME)
                                            .where("vote_message_id", e.getMessageId()).get();
                                        if (c.size() > 0) {
                                            User u = avaire.getShardManager().getUserById(c.get(0).getLong("voter_user_id"));
                                            if (u != null) {
                                                u.openPrivateChannel().queue(v -> v.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel()).setDescription("Congrats! Your vote for ``:Us`` has been approved. This means your vote is within against the total!")
                                                    .set("Us", c.get(0).getString("voted_for")).buildEmbed()).queue());
                                            }

                                            avaire.getDatabase().newQueryBuilder(Constants.MOTS_VOTE_TABLE_NAME).useAsync(true).where("vote_message_id", message.getId())
                                                .update(statement -> {
                                                    statement.set("accepted", true);
                                                });
                                        }
                                    } else if (e.getReactionEmote().getName().equals("❌")) {
                                        message.editmessageEmbeds(MessageFactory.makeEmbeddedMessage(message.getTextChannel(), new Color(255, 0, 0))
                                            .setAuthor(message.getEmbeds().get(0).getAuthor().getName(), null, message.getEmbeds().get(0).getAuthor().getIconUrl())
                                            .setDescription(message.getEmbeds().get(0).getDescription())
                                            .setFooter(message.getEmbeds().get(0).getFooter().getText() + " | Denied by: " + e.getMember().getEffectiveName())
                                            .setTimestamp(Instant.now()).buildEmbed()).queue();

                                        Collection c = avaire.getDatabase().newQueryBuilder(Constants.MOTS_VOTE_TABLE_NAME)
                                            .where("vote_message_id", e.getMessageId()).get();
                                        if (c.size() > 0) {
                                            User u = avaire.getShardManager().getUserById(c.get(0).getLong("voter_user_id"));
                                            if (u != null) {
                                                u.openPrivateChannel().queue(v -> v.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel()).setDescription("Sorry, but your vote for ``:Us`` has been rejected. Please vote again (Might have to be with a better reason) if you want to vote for this person.")
                                                    .set("Us", c.get(0).getString("voted_for")).buildEmbed()).queue());
                                            }
                                            avaire.getDatabase().newQueryBuilder(Constants.MOTS_VOTE_TABLE_NAME).useAsync(true)
                                                .where("vote_message_id", e.getMessageId())
                                                .delete();
                                        }
                                    }
                                    message.clearReactions().queue();
                                } catch (SQLException throwables) {
                                    AvaIre.getLogger().error("ERROR: ", throwables);
                                }
                            }
                        }

                    );
                }
            }
        });
    }

    private String getRole(GuildMessageReceivedEvent c) {
        return getString(c.getMember());
    }

    private String getRole(GuildMessageReactionAddEvent c) {
        return getString(c.getMember());
    }

    @NotNull
    private String getString(Member member) {
        return member.getRoles().size() > 0 ? member.getRoles().get(0).getAsMention() : "";
    }

    public void onReportsReactionAdd(GuildMessageReactionAddEvent e) {
        loadDatabasePropertiesIntoMemory(e).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuild().getHandbookReportChannel() != null) {
                TextChannel tc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuild().getHandbookReportChannel());
                if (tc != null) {
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
                            User memberAsReporter = avaire.getShardManager().getUserById(reporter);


                            if ("\uD83D\uDC4D".equals(e.getReactionEmote().getName())) { // 👍
                                e.getReaction().retrieveUsers().queue(react -> {
                                    if (react.size() > 30) {
                                        tc.retrieveMessageById(c.getLong("report_message_id")).queue(v -> {
                                            if (v.getEmbeds().get(0).getColor().equals(new Color(255, 120, 0))) return;
                                            v.editmessageEmbeds(MessageFactory.makeEmbeddedMessage(tc, new Color(255, 120, 0))
                                                .setAuthor("Report created for: " + username, null, getImageByName(tc.getGuild(), username))
                                                .setDescription(
                                                    "**Violator**: " + username + "\n" +
                                                        (rank != null ? "**Rank**: ``:rRank``\n" : "") +
                                                        "**Information**: \n" + description + "\n\n" +
                                                        "**Evidence**: \n" + evidence +
                                                        (warningEvidence != null ? "**Evidence of warning**:\n" + warningEvidence : ""))
                                                .requestedBy(memberAsReporter != null ? memberAsReporter : e.getMember().getUser())
                                                .setTimestamp(Instant.now()).set("rRank", rank)
                                                .buildEmbed())
                                                .queue();
                                            v.clearReactions("\uD83D\uDC4D").queue();
                                            v.clearReactions("\uD83D\uDC4E").queue();
                                        });
                                    }
                                });
                            }
                        } catch (SQLException throwables) {
                            AvaIre.getLogger().error("ERROR: ", throwables);
                        }

                    }
                }
            }
        });
    }

    public void onFeedbackMessageEvent(GuildMessageReactionAddEvent e) {
        if (e.getMember().getUser().isBot()) {
            return;
        }
        loadDatabasePropertiesIntoMemory(e).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuild().getSuggestionChannel() != null || databaseEventHolder.getGuild().getSuggestionCommunityChannel() != null) {
                try {
                    QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.PB_SUGGESTIONS_TABLE_NAME).where("pb_server_id", e.getGuild().getId()).andWhere("suggestion_message_id", e.getMessageId());

                    TextChannel tc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuild().getSuggestionChannel());
                    TextChannel ctc = null;
                    if (databaseEventHolder.getGuild().getSuggestionCommunityChannel() != null) {
                        if (avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuild().getSuggestionCommunityChannel()) != null) {
                            ctc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuild().getSuggestionCommunityChannel());
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
                            try {
                                if (e.getReactionEmote().getName().equals("\uD83D\uDC4D") | e.getReactionEmote().getName().equals("\uD83D\uDC4E")) {
                                    int likes = 0, dislikes = 0;
                                    for (MessageReaction reaction : msg.getReactions()) {
                                        if (reaction.getReactionEmote().getName().equals("\uD83D\uDC4D")) {
                                            likes = reaction.getCount();
                                        }

                                        if (reaction.getReactionEmote().getName().equals("\uD83D\uDC4E")) {
                                            dislikes = reaction.getCount();
                                        }
                                    }

                                    if (likes == 26) {
                                        net.dv8tion.jda.api.interactions.components.Button b1 = net.dv8tion.jda.api.interactions.components.Button.success("accept:" + e.getMessageId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
                                        net.dv8tion.jda.api.interactions.components.Button b2 = net.dv8tion.jda.api.interactions.components.Button.danger("reject:" + e.getMessageId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
                                        net.dv8tion.jda.api.interactions.components.Button b3 = net.dv8tion.jda.api.interactions.components.Button.secondary("remove:" + e.getMessageId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));
                                        net.dv8tion.jda.api.interactions.components.Button b4 = net.dv8tion.jda.api.interactions.components.Button.secondary("comment:" + e.getMessageId(), "Comment").withEmoji(Emoji.fromUnicode("\uD83D\uDCAC"));
                                        net.dv8tion.jda.api.interactions.components.Button b5 = Button.danger("community-move:" + e.getMessageId(), "Move to CAS").withEmoji(Emoji.fromUnicode("\uD83D\uDC51"));

                                        ActionRow actionRow = ActionRow.of(b1.asEnabled(), b2.asEnabled(), b3.asEnabled(), b4.asEnabled(), b5.asDisabled());


                                        if (finalCtc != null) {
                                            PlaceholderMessage mb = MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 100, 0))
                                                .setAuthor("Suggestion for: " + e.getGuild().getName() + " | " + likes + " - " + dislikes, null, e.getGuild().getIconUrl())
                                                .setDescription(msg.getEmbeds().get(0).getDescription())
                                                .setTimestamp(Instant.now());


                                            if (qb.get().size() < 1) {
                                                mb.setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl());
                                            } else {
                                                mb.requestedBy(m);
                                            }

                                            finalCtc.sendMessage(mb.buildEmbed()).setActionRows(actionRow).queue(p -> {
                                                /*
                                                p.addReaction("✅").queue();
                                                p.addReaction("❌").queue();
                                                p.addReaction("\uD83D\uDEAB").queue();
                                                p.addReaction("\uD83D\uDD04").queue();*/
                                                try {
                                                    qb.update(l -> {
                                                        l.set("suggestion_message_id", p.getId());
                                                    });
                                                } catch (SQLException throwables) {
                                                    AvaIre.getLogger().error("ERROR: ", throwables);
                                                }
                                            });
                                            msg.delete().queue();
                                            return;
                                        }

                                        PlaceholderMessage mb = MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 100, 0))
                                            .setAuthor("Suggestion for: " + e.getGuild().getName() + " | " + likes + " - " + dislikes, null, e.getGuild().getIconUrl())
                                            .setDescription(msg.getEmbeds().get(0).getDescription())
                                            .setTimestamp(Instant.now());

                                        if (qb.get().size() < 1) {
                                            mb.setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl());
                                        } else {
                                            mb.requestedBy(m);
                                        }

                                        msg.editMessage(mb.buildEmbed()).setActionRows(actionRow).queue();
                                        msg.clearReactions("\uD83D\uDC4D").queueAfter(1, TimeUnit.SECONDS);
                                        msg.clearReactions("\uD83D\uDC4E").queueAfter(1, TimeUnit.SECONDS);
                                    }

                                    if (dislikes == 26) {
                                        PlaceholderMessage mb = MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 100, 0))
                                            .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Denied by community", null, e.getGuild().getIconUrl())
                                            .setDescription(msg.getEmbeds().get(0).getDescription())
                                            .setTimestamp(Instant.now());

                                        if (qb.get().size() < 1) {
                                            mb.setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl());
                                        } else {
                                            mb.requestedBy(m);
                                        }

                                        msg.editMessage(mb.buildEmbed()).queue();
                                        msg.clearReactions().queue();
                                        qb.delete();

                                    }
                                }
                                if (e.getReactionEmote().getName().equals("❌") || e.getReactionEmote().getName().equals("✅") || e.getReactionEmote().getName().equals("\uD83D\uDD04")) {
                                    switch (e.getReactionEmote().getName()) {
                                        case "❌":
                                            if (!(isValidReportManager(e, 2))) {
                                                /*e.getChannel().sendMessage(e.getMember().getAsMention()).embed(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Sorry, but you're not allowed to deny this suggestion. You have to be at least a **Manager** to do this.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(v -> {
                                                    v.delete().queueAfter(30, TimeUnit.SECONDS);
                                                });*/
                                                e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
                                                return;
                                            }
                                            msg.editmessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0))
                                                .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Denied by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                .setDescription(msg.getEmbeds().get(0).getDescription())
                                                .setTimestamp(Instant.now())
                                                .buildEmbed()).queue();
                                            msg.clearReactions().queue();
                                            qb.delete();
                                            break;
                                        case "✅":
                                            if (!(isValidReportManager(e, 2))) {
                                                /*e.getChannel().sendMessage(e.getMember().getAsMention()).embed(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Sorry, but you're not allowed to approve this suggestion. You have to be at least a **Manager** to do this.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(v -> {
                                                    v.delete().queueAfter(30, TimeUnit.SECONDS);
                                                });*/
                                                e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
                                                return;
                                            }

                                            if (databaseEventHolder.getGuild().getSuggestionApprovedChannelId() != null) {
                                                TextChannel atc = avaire.getShardManager().getTextChannelById(databaseEventHolder.getGuild().getSuggestionApprovedChannelId());
                                                if (atc != null) {
                                                    atc.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(0, 255, 0))
                                                        .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Approved by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                        .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                        .setDescription(msg.getEmbeds().get(0).getDescription())
                                                        .setTimestamp(Instant.now())
                                                        .buildEmbed()).queue();
                                                    msg.delete().queue();
                                                } else {
                                                    msg.editmessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(0, 255, 0))
                                                        .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Approved by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                        .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                        .setDescription(msg.getEmbeds().get(0).getDescription())
                                                        .setTimestamp(Instant.now())
                                                        .buildEmbed()).queue();
                                                    msg.clearReactions().queue();

                                                }
                                            } else {
                                                msg.editmessageEmbeds(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(0, 255, 0))
                                                    .setAuthor("Suggestion for: " + e.getGuild().getName() + " | Approved by: " + e.getMember().getEffectiveName(), null, e.getGuild().getIconUrl())
                                                    .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl())
                                                    .setDescription(msg.getEmbeds().get(0).getDescription())
                                                    .setTimestamp(Instant.now())
                                                    .buildEmbed()).queue();
                                                msg.clearReactions().queue();

                                            }

                                            qb.delete();
                                            break;
                                        case "\uD83D\uDD04":
                                            if (!(isValidReportManager(e, 2))) {
                                                /*e.getChannel().sendMessage(e.getMember().getAsMention()).embed(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Sorry, but you're not allowed to refresh this suggestion's icons. You have to be at least a **Manager** to do this.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(v -> {
                                                    v.delete().queueAfter(30, TimeUnit.SECONDS);
                                                });*/
                                                e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
                                                return;
                                            }
                                            msg.clearReactions().queue();
                                            msg.addReaction("\uD83D\uDC4D").queue(); //
                                            msg.addReaction("\uD83D\uDC4E").queue(); // 👎
                                            msg.addReaction("✅").queue();
                                            msg.addReaction("❌").queue();
                                            msg.addReaction("\uD83D\uDCAC").queue();
                                            msg.addReaction("\uD83D\uDEAB").queue(); // 🚫
                                            msg.addReaction("\uD83D\uDD04").queue(); // 🔄
                                    }
                                }
                                if (e.getReactionEmote().getName().equals("\uD83D\uDEAB")) { //
                                    if (!(isValidReportManager(e, 1))) {
                                        /*e.getChannel().sendMessage(e.getMember().getAsMention()).embed(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Sorry, but you're not allowed to delete this suggestion. You have to be at least a **Mod** to do this.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(v -> {
                                            v.delete().queueAfter(30, TimeUnit.SECONDS);
                                        });*/
                                        e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
                                        return;
                                    }
                                    msg.delete().queue();
                                } //🚫
                                if (e.getReactionEmote().getName().equals("\uD83D\uDCAC")) {
                                    if (!(e.getMember().hasPermission(Permission.MESSAGE_MANAGE) || isValidReportManager(e, 1))) {
                                        /*e.getMember().getUser().openPrivateChannel().queue(v -> v.sendMessage(new EmbedBuilder().setDescription("Sorry, but you need to be an mod or higher to comment on a suggestion!").build()).queue());
                                         */
                                        e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
                                        return;
                                    }
                                    e.getReaction().removeReaction(e.getUser()).queue();

                                    if (isValidReportManager(e, 1)) {
                                        msg.getTextChannel().sendMessage(e.getMember().getAsMention() + "\nWhat is your comment?").queue(
                                            v -> avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, c -> c.getChannel().equals(e.getChannel()) && c.getMember().equals(e.getMember()), c -> {
                                                v.delete().queue();
                                                msg.editMessage(new EmbedBuilder()
                                                    .setColor(msg.getEmbeds().get(0).getColor())
                                                    .setAuthor("Suggestion for: " + e.getGuild().getName(), null, e.getGuild().getIconUrl())
                                                    .setDescription(msg.getEmbeds().get(0).getDescription() + "\n\n" + getRole(c) + " - :speech_balloon: **``" + e.getMember().getEffectiveName() + "``:**\n" + c.getMessage().getContentRaw())
                                                    .setTimestamp(msg.getEmbeds().get(0).getTimestamp())
                                                    .setFooter(msg.getEmbeds().get(0).getFooter().getText(), msg.getEmbeds().get(0).getFooter().getIconUrl()).build()).queue();
                                                c.getMessage().delete().queue();
                                                if (e.getGuild().getMembersByEffectiveName(msg.getEmbeds().get(0).getFooter().getText(), true).size() > 0) {
                                                    for (Member u : e.getGuild().getMembersByEffectiveName(msg.getEmbeds().get(0).getFooter().getText(), true)) {
                                                        u.getUser().openPrivateChannel().complete()
                                                            .sendMessage(new EmbedBuilder()
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
                                if (e.getReactionEmote().getName().equals("\uD83D\uDC51")) {
                                    if (!(isValidReportManager(e, 1))) {
                                        /*e.getChannel().sendMessage(e.getMember().getAsMention()).embed(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("Sorry, but you're not allowed to delete this suggestion. You have to be at least a **Mod** to do this.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(v -> {
                                            v.delete().queueAfter(30, TimeUnit.SECONDS);
                                        });*/
                                        e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
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

                                        finalCtc.sendMessage(mb.buildEmbed()).queue(p -> {
                                            p.addReaction("✅").queue();
                                            p.addReaction("❌").queue();
                                            p.addReaction("\uD83D\uDEAB").queue();
                                            p.addReaction("\uD83D\uDD04").queue();
                                            p.addReaction("\uD83D\uDCAC").queue(); // 💬
                                            try {
                                                qb.update(l -> {
                                                    l.set("suggestion_message_id", p.getId());
                                                });
                                            } catch (SQLException throwables) {
                                                AvaIre.getLogger().error("ERROR: ", throwables);
                                            }
                                        });
                                        msg.delete().queue();
                                    } else {
                                        /*e.getChannel().sendMessage(e.getMember().getAsMention()).embed(MessageFactory.makeEmbeddedMessage(e.getChannel(), new Color(255, 0, 0)).setDescription("This guild does not have a community suggestion channel set.").setFooter("This message will self-destruct in 30s").buildEmbed()).queue(v -> {
                                            v.delete().queueAfter(30, TimeUnit.SECONDS);
                                        });*/
                                        e.getReaction().removeReaction(e.getUser()).queueAfter(1, TimeUnit.SECONDS);
                                    }

                                } //👑
                            } catch (SQLException throwables) {
                                AvaIre.getLogger().error("ERROR: ", throwables);
                            }
                        });
                    }
                } catch (SQLException throwables) {
                    AvaIre.getLogger().error("ERROR: ", throwables);
                }
            }
        });
    }

    private String getImageByName(Guild guild, String username) {
        List<Member> members = guild.getMembersByEffectiveName(username, true);

        if (members.size() < 1) return null;
        if (members.size() > 1) return null;
        else return members.get(0).getUser().getEffectiveAvatarUrl();
    }

    private void count(GuildMessageReactionAddEvent event, Message m, Message v, GuildMessageReceivedEvent c) {
        int likes = 0, dislikes = 0;
        for (MessageReaction reaction : m.getReactions()) {
            if (reaction.getReactionEmote().getName().equals("\uD83D\uDC4D")) {
                likes = reaction.getCount();
            }

            if (reaction.getReactionEmote().getName().equals("\uD83D\uDC4E")) {
                dislikes = reaction.getCount();
            }
        }
        m.editMessage(new EmbedBuilder()
            .setDescription(m.getEmbeds().get(0).getDescription() + "\n\n**Denial Reason given by " + getRole(event) + " " + event.getMember().getEffectiveName() + "**: \n" + c.getMessage().getContentRaw() + "\n\n**Public vote**:\n :+1: - " + likes + "\n:-1: - " + dislikes)
            .setTitle(m.getEmbeds().get(0).getTitle() + " | Denied by " + c.getMember().getEffectiveName())
            .setFooter(m.getEmbeds().get(0).getFooter().getText(), m.getEmbeds().get(0).getFooter().getIconUrl())
            .setTimestamp(m.getEmbeds().get(0).getTimestamp())
            .setColor(new Color(255, 0, 0))
            .build()).queue();

        v.delete().queue();
        c.getMessage().delete().queue();
        m.clearReactions().queue();
    }

    public void onPBSTRequestRewardMessageAddEvent(GuildMessageReactionAddEvent event) {
        MessageReaction.ReactionEmote emote = event.getReactionEmote();
        event.getChannel().retrieveMessageById(event.getMessageId()).queue(m -> {
            if (emote.getName().equals("\uD83D\uDC4D") | emote.getName().equals("\uD83D\uDC4E")) {
            /*if (m.getEmbeds().get(0).getTitle().equals(event.getMember().getEffectiveName()) || m.getEmbeds().get(0).getFooter().getText().contains(event.getMember().getEffectiveName())) {
                event.getReaction().removeReaction(event.getUser()).queue();
                event.getMember().getUser().openPrivateChannel().complete().sendMessage("You reacted to a reward request that includes your name, you cannot do this.").queue();
                return;
            }*/
            }
            if (emote.getName().equals("✅") || emote.getName().equals("❌")) {
                if (!(isValidReportManager(event, 1) || event.getMember().getId().equals("173839105615069184"))) {
                    event.getMember().getUser().openPrivateChannel().queue(v -> v.sendMessage(new EmbedBuilder().setDescription("Sorry, but you need to be the manager of points in your division to approve or deny a reward request.").build()).queue());
                    event.getReaction().removeReaction(event.getUser()).queueAfter(1, TimeUnit.SECONDS);
                    return;
                }

                if (emote.getName().equals("✅")) {
                    event.getReaction().removeReaction(event.getUser()).queueAfter(1, TimeUnit.SECONDS);
                    event.getChannel().sendMessage(event.getMember().getAsMention() + "\n" +
                        "You've chosen to approve this reward request, what reward will you give?").queue(v ->
                        avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, c -> c.getChannel().equals(event.getChannel()) && c.getMember().equals(event.getMember()), c -> {
                            int likes = 0, dislikes = 0;
                            for (MessageReaction reaction : m.getReactions()) {
                                if (reaction.getReactionEmote().getName().equals("\uD83D\uDC4D")) {
                                    likes = reaction.getCount();
                                }

                                if (reaction.getReactionEmote().getName().equals("\uD83D\uDC4E")) {
                                    dislikes = reaction.getCount();
                                }
                            }
                            m.editMessage(new EmbedBuilder()
                                .setDescription(m.getEmbeds().get(0).getDescription() + "\n\n**Reward given by " + getRole(event) + " " + event.getMember().getEffectiveName() + "**: \n" + c.getMessage().getContentRaw() + "\n\n**Public vote**:\n :+1: - " + likes + "\n:-1: - " + dislikes)
                                .setTitle(m.getEmbeds().get(0).getTitle() + " | Approved by " + c.getMember().getEffectiveName())
                                .setTimestamp(m.getEmbeds().get(0).getTimestamp())
                                .setFooter(m.getEmbeds().get(0).getFooter().getText(), m.getEmbeds().get(0).getFooter().getIconUrl())
                                .setColor(new Color(0, 255, 0))
                                .build()).queue();

                            v.delete().queue();
                            c.getMessage().delete().queue();
                            m.clearReactions().queue();
                            if (event.getGuild().getMembersByEffectiveName(m.getEmbeds().get(0).getFooter().getText(), true).size() > 0) {
                                for (Member u : event.getGuild().getMembersByEffectiveName(m.getEmbeds().get(0).getTitle(), true)) {
                                    u.getUser().openPrivateChannel().complete()
                                        .sendMessage(new EmbedBuilder()
                                            .setDescription("Hello there ``" + u.getEffectiveName() + "``.\n" +
                                                "It seems like you have gotten reward!\n" +
                                                "If you want to check the report, [click here](" + m.getJumpUrl() + ")\n" +
                                                "The reward was given by **" + event.getMember().getEffectiveName() + "** in ``" + event.getGuild().getName() + "``!\n\n" +
                                                "**The reward**:\n" + c.getMessage().getContentRaw()).build()).queue();
                                }
                            }
                        }, 90, TimeUnit.SECONDS, () -> {
                            v.delete().queue();
                            m.getMember().getUser().openPrivateChannel().complete().sendMessage("You took to long to send a reaction, please re-react to the message!").queue();
                        }));

                }
                if (emote.getName().equals("❌")) {
                    event.getReaction().removeReaction(event.getUser()).queueAfter(1, TimeUnit.SECONDS);

                    event.getChannel().sendMessage(event.getMember().getAsMention() + "\n" +
                        "You've chosen to deny this reward request, what reason will you give?").queue(v -> avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, c -> c.getChannel().equals(event.getChannel()) && c.getMember().equals(event.getMember()), c -> {
                        count(event, m, v, c);
                        if (event.getGuild().getMembersByEffectiveName(m.getEmbeds().get(0).getFooter().getText(), true).size() > 0) {
                            for (Member u : event.getGuild().getMembersByEffectiveName(m.getEmbeds().get(0).getFooter().getText(), true)) {
                                u.getUser().openPrivateChannel().complete()
                                    .sendMessage(new EmbedBuilder()
                                        .setColor(new Color(255, 0, 0))
                                        .setDescription("Hello there ``" + u.getEffectiveName() + "``.\n" +
                                            "It seems like your reward request on ``" + m.getEmbeds().get(0).getTitle() + "`` has been denied!\n" +
                                            "If you want to check the reward request, [click here](" + m.getJumpUrl() + ")\n" +
                                            "The denial was given by **" + event.getMember().getEffectiveName() + "** in ``" + event.getGuild().getName() + "``!\n\n" +
                                            "**The reason**:\n" + c.getMessage().getContentRaw()).build()).queue();
                            }
                        }
                    }, 90, TimeUnit.SECONDS, () -> {
                        v.delete().queue();
                        m.getMember().getUser().openPrivateChannel().complete().sendMessage("You took to long to send a comment, please re-react to the message!").queue();
                    }));

                }
            }
            if (emote.getName().equals("trash")) {
                if (!(isValidReportManager(event, 1) || event.getMember().getId().equals("173839105615069184"))) {
                    event.getMember().getUser().openPrivateChannel().queue(v -> v.sendMessage(new EmbedBuilder().setDescription("Sorry, but you need to be the manager of points in your division to remove a report.").build()).queue());
                    event.getReaction().removeReaction(event.getUser()).queueAfter(1, TimeUnit.SECONDS);
                    return;
                }
                m.delete().queue();
            }
            if (emote.getName().equals("\uD83D\uDD04")) {
                if (!(isValidReportManager(event, 1) || event.getMember().getId().equals("173839105615069184"))) {
                    event.getMember().getUser().openPrivateChannel().queue(v -> v.sendMessage(new EmbedBuilder().setDescription("Sorry, but you need to be the manager of points in your division to refresh the emoji's.").build()).queue());
                    event.getReaction().removeReaction(event.getUser()).queueAfter(1, TimeUnit.SECONDS);
                    return;
                }
                m.clearReactions().queue();
                m.addReaction("\uD83D\uDC4D").queue();
                m.addReaction("\uD83D\uDC4E").queue();
                m.addReaction("✅").queue();
                m.addReaction("❌").queue();
                m.addReaction("🚫").queue();
                m.addReaction("\uD83D\uDD04").queue();
            }
        });
    }

    private boolean isValidReportManager(GuildMessageReactionAddEvent e, Integer i) {
        GuildTransformer transformer = GuildController.fetchGuild(avaire, e.getGuild());
        if (i == 1) {
            return CheckPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getMember()).getLevel() >= CheckPermissionUtil.GuildPermissionCheckType.MOD.getLevel();
        }
        if (i == 2) {
            return CheckPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getMember()).getLevel() >= CheckPermissionUtil.GuildPermissionCheckType.MANAGER.getLevel();
        }
        if (i == 3) {
            return CheckPermissionUtil.getPermissionLevel(transformer, e.getGuild(), e.getMember()).getLevel() >= CheckPermissionUtil.GuildPermissionCheckType.ADMIN.getLevel();
        }
        return false;
    }


    private CompletableFuture<DatabaseEventHolder> loadDatabasePropertiesIntoMemory(
        final GuildMessageReactionAddEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            if (!event.getChannel().getType().isGuild()) {
                return new DatabaseEventHolder(null, null, null);
            }

            GuildTransformer guild = GuildController.fetchGuild(avaire, event.getGuild());

            if (guild == null || !guild.isLevels() || event.getMember().getUser().isBot()) {
                return new DatabaseEventHolder(guild, null, null);
            }
            return new DatabaseEventHolder(guild, null, null);
        });
    }


}

