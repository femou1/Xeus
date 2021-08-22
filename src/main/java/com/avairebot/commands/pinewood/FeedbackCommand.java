package com.avairebot.commands.pinewood;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.blacklist.features.FeatureScope;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.contracts.commands.CommandGroup;
import com.avairebot.contracts.commands.CommandGroups;
import com.avairebot.database.collection.DataRow;
import com.avairebot.database.query.QueryBuilder;
import com.avairebot.database.transformers.GuildTransformer;
import com.avairebot.utilities.CheckPermissionUtil;
import com.avairebot.utilities.EventWaiter;
import com.avairebot.utilities.MentionableUtil;
import com.avairebot.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FeedbackCommand extends Command {

    public FeedbackCommand(AvaIre avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Global Feedback Command";
    }

    @Override
    public String getDescription() {
        return "Feedback about something in an guild.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Submit a suggestion to any guild having this feature enabled."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Submit a suggestion to any guild having this feature enabled."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("suggest", "feedback");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.COMMAND_CUSTOMIZATION
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isOfficialPinewoodGuild",
            "throttle:user,1,120"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (context.member == null) {
            return false;
        }

        int permissionLevel = CheckPermissionUtil.getPermissionLevel(context).getLevel();
        if (permissionLevel >= CheckPermissionUtil.GuildPermissionCheckType.MANAGER.getLevel()) {
            if (args.length > 0) {
                switch (args[0]) {
                    case "ss":
                    case "set-suggestions":
                        return runSetSuggestionChannel(context, args);
                    case "sc":
                    case "set-community":
                        return runSetCommunityVotesChannel(context, args);
                    case "cch":
                    case "change-community-threshold":
                        return runChangeCommunityThreshold(context, args);
                    case "sasc":
                    case "set-approved-suggestions-channel":
                        return runSetApprovedSuggestionsChannel(context, args);
                    case "ca":
                    case "clear-all":
                        return runClearAllChannelsFromDatabase(context);
                    default:
                        return sendErrorMessage(context, "Please enter in a correct argument.");
                }
            }
        }

        context.makeInfo("<a:loading:742658561414266890> Loading suggestions... <a:loading:742658561414266890>").queue(l -> {

            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME).orderBy("suggestion_channel");
            try {
                StringBuilder sb = new StringBuilder();
                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("suggestion_channel") != null) {
                        Guild g = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                        Emote e = avaire.getShardManager().getEmoteById(dataRow.getString("suggestion_emote_id"));

                        if (g != null && e != null) {
                            sb.append("``").append(g.getName()).append("`` - ").append(e.getAsMention()).append("\n");
                            l.addReaction(e).queue();
                        } else {
                            context.makeError("Either the guild or the emote can't be found in the database, please check with the developer.").queue();
                            return;
                        }
                    }
                });
                l.editMessageEmbeds(context.makeInfo("Welcome to the pinewood suggestion system, please submit a suggestion for any of the selected guilds.\nIf you want to suggest a feature for Xeus, [then please go to the Xeus issue's page, and create a suggestion](https://gitlab.com/pinewood-builders/discord/xeus/-/issues).\n\n" + sb.toString()).buildEmbed()).queue();

                startEmojiWaiter(context, l, avaire.getWaiter(), qb);
            } catch (SQLException throwables) {
                AvaIre.getLogger().error("ERROR: ", throwables);
            }

        });


        return true;
    }

    private boolean runChangeCommunityThreshold(CommandMessage context, String[] args) {
        return false;
    }

    private void startEmojiWaiter(CommandMessage context, Message message, EventWaiter waiter, QueryBuilder qb) {
        waiter.waitForEvent(GuildMessageReactionAddEvent.class, l -> l.getMember().equals(context.member) && message.getId().equals(l.getMessageId()), emote -> {
            try {
                DataRow d = qb.where("suggestion_emote_id", emote.getReactionEmote().getId()).get().get(0);

                TextChannel c = avaire.getShardManager().getTextChannelById(d.getString("suggestion_channel"));
                if (c != null) {

                    if (avaire.getFeatureBlacklist().isBlacklisted(context.getAuthor(), c.getGuild().getIdLong(), FeatureScope.SUGGESTIONS)) {
                        message.editMessageEmbeds(context.makeError("You have been blacklisted from creating suggestions for this guild. Please ask a **Level 4** or higher to remove you from the ``"+c.getGuild().getName()+"`` suggestion blacklist. (Or global, if you're globally banned from all features)").buildEmbed()).queue();
                        return;
                    }

                    message.editMessageEmbeds(context.makeInfo("You've selected a suggestion for: ``:guild``\nPlease tell me, what is your suggestion?").set("guild", d.getString("name")).buildEmbed()).queue();
                    message.clearReactions().queue();


                    waiter.waitForEvent(GuildMessageReceivedEvent.class, l -> {
                        Member m = l.getMember();
                        return m != null && m.equals(context.member) && message.getChannel().equals(l.getChannel()) && antiSpamInfo(context, l);
                    }, p -> {
                        if (p.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                            context.makeInfo("Cancelled suggestion.").queue();
                            return;
                        }


                        Button b1 = Button.success("accept:" + message.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
                        Button b2 = Button.danger("reject:" + message.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
                        Button b3 = Button.secondary("remove:" + message.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));
                        Button b4 = Button.secondary("comment:" + message.getId(), "Comment").withEmoji(Emoji.fromUnicode("\uD83D\uDCAC"));
                        Button b5 = Button.secondary("community-move:" + message.getId(), "Move to CAS").withEmoji(Emoji.fromUnicode("\uD83D\uDC51"));

                        ActionRow actionRow;
                        if (d.getString("suggestion_community_channel") != null) {
                            actionRow = ActionRow.of(b1.asEnabled(), b2.asEnabled(), b3.asEnabled(), b4.asEnabled(), b5.asEnabled());
                        } else {
                            actionRow = ActionRow.of(b1.asEnabled(), b2.asEnabled(), b3.asEnabled(), b4.asEnabled(), b5.asDisabled());
                        }

                        c.sendMessageEmbeds(context.makeEmbeddedMessage(new Color(32, 34, 37))
                            .setAuthor("Suggestion for: " + c.getGuild().getName(), null, c.getGuild().getIconUrl())
                            .requestedBy(context.member).setDescription(p.getMessage().getContentRaw())
                            .setTimestamp(Instant.now())
                            .buildEmbed()).setActionRows(actionRow).queue(v -> {
                            context.makeSuccess("[Your suggestion has been posted in the correct suggestion channel.](:link)").set("link", v.getJumpUrl()).queue();
                            createReactions(v, d.getString("suggestion_community_channel"));

                            try {
                                avaire.getDatabase().newQueryBuilder(Constants.PB_SUGGESTIONS_TABLE_NAME).insert(data -> {
                                    data.set("pb_server_id", d.getString("id"));
                                    data.set("suggestion_message_id", v.getId());
                                    data.set("suggester_discord_id", context.getMember().getId());
                                });
                            } catch (SQLException throwables) {
                                context.makeError("Something went wrong in the database, please check with the developer.").queue();
                                AvaIre.getLogger().error("ERROR: ", throwables);
                            }
                        });
                    });
                } else {
                    context.makeError("This guild doesn't have a (valid) channel for suggestions").queue();
                }

            } catch (SQLException throwables) {
                AvaIre.getLogger().error("ERROR: ", throwables);
            }
        }, 5, TimeUnit.MINUTES, () -> {
            message.editMessageEmbeds(context.makeEmbeddedMessage().setColor(Color.BLACK).setDescription("Stopped the suggestion system. Timeout of 5 minutes reached .").buildEmbed()).queue();
        });
    }

    private boolean antiSpamInfo(CommandMessage context, GuildMessageReceivedEvent l) {
        if (l.getMessage().getContentRaw().equalsIgnoreCase("cancel")) return true;

        if (l.getMessage().getContentRaw().length() <= 25) {
            context.makeError("Please make sure your suggestion has more then 25 characters before submitting it. If you want to cancel. Say \"cancel\"").queue();
            return false;
        } else {
            return true;
        }
    }

    private boolean runClearAllChannelsFromDatabase(CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_channel", null);
                q.set("suggestion_emote_id", null);
                q.set("suggestion_community_channel", null);
                q.set("approved_suggestion_channel", null);
            });

            context.makeSuccess("Any information about the suggestion channel has been removed from the database.").queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    private boolean runSetApprovedSuggestionsChannel(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }

        if (transformer.getSuggestionChannel() == null) {
            context.makeError("You want to set a approved suggestion channel, without the suggestions channel being set. Please set a \"Suggestion Channel\" with ``:command set-suggestions <channel> <emote>``").set("command", generateCommandTrigger(context.message)).queue();
            return false;
        }

        GuildChannel channel = MentionableUtil.getChannel(context.message, args, 1);
        if (channel == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }


        return updateApprovedSuggestionChannelInDatabase(transformer, context, (TextChannel) channel);
    }

    private boolean updateApprovedSuggestionChannelInDatabase(GuildTransformer transformer, CommandMessage context, TextChannel channel) {
        transformer.setSuggestionApprovedChannelId(channel.getId());

        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("approved_suggestion_channel", transformer.getSuggestionApprovedChannelId());
            });
            context.makeSuccess("Set the approved suggestion channel to " + channel.getAsMention()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    private boolean runSetCommunityVotesChannel(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }

        if (transformer.getSuggestionChannel() == null) {
            context.makeError("You want to set a community approved suggestion channel, without the suggestions channel being set. Please set a \"Suggestion Channel\" with ``:command set-suggestions <channel> <emote>``").set("command", generateCommandTrigger(context.message)).queue();
            return false;
        }

        GuildChannel channel = MentionableUtil.getChannel(context.message, args, 1);
        if (channel == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }


        return updateCommunityChannelInDatabase(transformer, context, (TextChannel) channel);
    }

    private boolean updateCommunityChannelInDatabase(GuildTransformer transformer, CommandMessage context, TextChannel channel) {
        transformer.setSuggestionCommunityChannel(channel.getId());

        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_community_channel", transformer.getSuggestionCommunityChannel());
            });
            context.makeSuccess("Set the community channel to " + channel.getAsMention()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    private boolean runSetSuggestionChannel(CommandMessage context, String[] args) {
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

        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }

        transformer.setSuggestionChannel(c.getId());
        transformer.setSuggestionEmoteId(e.getId());

        return updateChannelAndEmote(context, transformer.getSuggestionEmoteId(), transformer.getSuggestionChannel());
    }



    private boolean updateChannelAndEmote(CommandMessage context, String emoteId, String suggestionChannel) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_channel", suggestionChannel);
                q.set("suggestion_emote_id", emoteId);
            });

            context.makeSuccess("Suggestions have been enabled for <#:channelId> with the emote <:F::emoteId>").set("channelId", suggestionChannel).set("emoteId", emoteId).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    public static void createReactions(Message r, String communityApprovedSuggestion) {
        r.addReaction("\uD83D\uDC4D").queue();   // 👍
        r.addReaction("\uD83D\uDC4E").queue();  // 👎
        /*r.addReaction("✅").queue();
        r.addReaction("❌").queue();
        r.addReaction("🚫").queue();
        r.addReaction("\uD83D\uDCAC").queue(); // 💬
        r.addReaction("\uD83D\uDD04").queue(); // 🔄

        if (communityApprovedSuggestion != null) {
            r.addReaction("\uD83D\uDC51").queue(); // 👑
        }*/

    }
}
