package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.blacklist.features.FeatureScope;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.utilities.EventWaiter;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SuggestionCommand extends Command {

    public SuggestionCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Global Suggestion Command";
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
            "isPinewoodGuild",
            "throttle:user,1,120"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (context.member == null) {
            return false;
        }

        int permissionLevel = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (permissionLevel >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
            if (args.length > 0) {
                return switch (args[0].toLowerCase()) {
                    case "ss", "set-suggestions " -> runSetSuggestionChannel(context, args);
                    case "sc", "set-community" -> runSetCommunityVotesChannel(context, args);
                    case "cch", "change-community-threshold" -> runChangeCommunityThreshold(context, args);
                    case "sasc", "set-approved-suggestions-channel" -> runSetApprovedSuggestionsChannel(context, args);
                    case "ca", "clear-all" -> runClearAllChannelsFromDatabase(context);
                    default -> sendErrorMessage(context, "Please enter in a correct argument.");
                };
            }
        }

        context.makeInfo("<a:loading:742658561414266890> Loading suggestions... <a:loading:742658561414266890>").queue(l -> {
            SelectMenu.Builder menu = SelectMenu.create("selector:server-to-suggest-to:" + context.getMember().getId() + ":" + context.getMessage().getId())
                .setPlaceholder("Select the place to suggest to!") // shows the placeholder indicating what this menu is for
                .addOption("Cancel", "cancel", "Stop offering suggestions", Emoji.fromUnicode("❌"))
                .setRequiredRange(1, 1); // only one can be selected


            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                .orderBy("suggestion_channel_id");
            try {
                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("suggestion_channel_id") != null) {
                        Guild g = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                        RichCustomEmoji e = avaire.getShardManager().getEmojiById(dataRow.getString("emoji_id"));

                        if (g != null && e != null) {
                            menu.addOption(g.getName(), g.getId() + ":" + e.getId(), "Suggest to " + g.getName(), Emoji.fromCustom(e));
                            //sb.append("``").append(g.getName()).append("`` - ").append(e.getAsMention()).append("\n");
                            //l.addReaction(e).queue();
                        } else {
                            context.makeError("Either the guild or the emote can't be found in the database, please check with the developer.").queue();
                        }
                    }
                });
                l.editMessageEmbeds(context.makeInfo("Welcome to the pinewood suggestion system, please submit a suggestion for any of the selected guilds.\nIf you want to suggest a feature for Xeus, [then please go to the Xeus issue's page, and create a suggestion](https://gitlab.com/pinewood-builders/discord/xeus/-/issues).").buildEmbed())
                    .setActionRow(menu.build()).queue();

                startMenuWaiter(context.member, l, avaire.getWaiter(), qb);
            } catch (SQLException throwable) {
                Xeus.getLogger().error("ERROR: ", throwable);
            }

        });


        return true;
    }

    private boolean runChangeCommunityThreshold(CommandMessage context, String[] args) {
        return false;
    }

    private void startMenuWaiter(Member member, Message message, EventWaiter waiter, QueryBuilder qb) {
        waiter.waitForEvent(SelectMenuInteractionEvent.class, l -> l.getMember().equals(member) && message.getId().equals(l.getMessageId()), emote -> {

            if (emote.getInteraction().getSelectedOptions().get(0).getEmoji().getName().equalsIgnoreCase("❌")) {
                message.editMessageEmbeds(MessageFactory.makeWarning(message, "Cancelled the system").buildEmbed()).setActionRows(Collections.emptySet()).queue();
                /*message.clearReactions().queue();*/
                return;
            }

            String[] split = emote.getInteraction().getSelectedOptions().get(0).getValue().split(":");
            String guildId = split[0];

            Guild guild = avaire.getShardManager().getGuildById(guildId);
            if (guild == null) {emote.reply("Something went wrong, please try again.").queue(); return;}

            GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guild);
            if (transformer.getSuggestionChannelId() == 0) {emote.reply("The suggestion ID doesn't exist. Please contact an admin+").queue(); return;}

            TextChannel c = avaire.getShardManager().getTextChannelById(transformer.getSuggestionChannelId());
            if (c != null) {

                if (avaire.getFeatureBlacklist().isBlacklisted(member.getUser(), c.getGuild().getIdLong(), FeatureScope.SUGGESTIONS)) {
                    message.editMessageEmbeds(MessageFactory.makeError(message, "You have been blacklisted from creating suggestions for this guild. Please ask a **Level 4** or higher to remove you from the `"+c.getGuild().getName()+"` suggestion blacklist. (Or global, if you're globally banned from all features)")
                        .buildEmbed())
                        .setActionRows(Collections.emptySet())
                        .queue();
                    return;
                }

                message.editMessageEmbeds(MessageFactory.makeError(message, "You have been sent a modal, please respond to the questions asked and come back here, if you're not back in 3 minutes. The modal expires").buildEmbed()).setActionRows(Collections.emptySet()).queue();

                TextInput suggestion = TextInput.create("suggestion", "Type your suggestion here!", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("This can be anything, just make sure it's appropriate for the guild.")
                    .setMinLength(10)
                    .setMaxLength(1000)
                    .build();

                Modal.Builder modal = Modal.create("report:" + guildId + ":" + message.getId(), guild.getName())
                    .addActionRows(ActionRow.of(suggestion));

                emote.replyModal(modal.build()).queue();
                //message.clearReactions().queue();


                waiter.waitForEvent(ModalInteractionEvent.class, l -> {
                    Member m = l.getMember();
                    return m != null && m.equals(member) && message.getChannel().equals(l.getChannel());
                }, p -> {
                    goToStep2(message, c, p, member, transformer);
                });
            } else {
                emote.reply("This guild doesn't have a (valid) channel for suggestions").queue();
            }

        }, 5, TimeUnit.MINUTES, () -> {
            message.editMessageEmbeds(MessageFactory.makeEmbeddedMessage(message.getChannel()).setColor(Color.BLACK).setDescription("Stopped the suggestion system. Timeout of 5 minutes reached .").buildEmbed()).queue();
        });
    }

    private void goToStep2(Message message, TextChannel c, ModalInteractionEvent p, Member member, GuildSettingsTransformer transformer) {
        Button b1 = Button.success("accept:" + message.getId(), "Accept").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.danger("reject:" + message.getId(), "Reject").withEmoji(Emoji.fromUnicode("❌"));
        Button b3 = Button.secondary("remove:" + message.getId(), "Delete").withEmoji(Emoji.fromUnicode("\uD83D\uDEAB"));
        Button b4 = Button.secondary("comment:" + message.getId(), "Comment").withEmoji(Emoji.fromUnicode("\uD83D\uDCAC"));
        Button b5 = Button.secondary("community-move:" + message.getId(), "Move to CAS").withEmoji(Emoji.fromUnicode("\uD83D\uDC51"));

        ModalMapping suggestionModal = p.getValue("suggestion");

        ActionRow actionRow =
            ActionRow.of(b1.asEnabled(),
            b2.asEnabled(),
            b3.asEnabled(),
            b4.asEnabled(),
                transformer.getSuggestionCommunityChannelId() != 0 ? b5.asEnabled() : b5.asDisabled()
            );

        c.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(c).setColor(new Color(32, 34, 37))
            .setAuthor("Suggestion for: " + c.getGuild().getName(), null, c.getGuild().getIconUrl())
            .requestedBy(member).setDescription(suggestionModal != null ? suggestionModal.getAsString() : "No suggestion provided.")
            .setTimestamp(Instant.now())
            .buildEmbed()).setActionRows(actionRow).queue(v -> {
            p.replyEmbeds(MessageFactory.
                makeSuccess(message, "[Your suggestion has been posted in the correct suggestion channel.](:link)")
                .set("link", v.getJumpUrl()).buildEmbed()).setEphemeral(true).queue();

            message.editMessage("The modal has been correctly received and the suggestion has been sent.")
                .setEmbeds(Collections.emptySet())
                .setActionRows(Collections.emptySet())
                .queue();

            createReactions(v);

            if (v.getGuild().getId().equals("438134543837560832")) v.createThreadChannel("Suggestion - " + member.getEffectiveName()).queue();

            try {
                avaire.getDatabase().newQueryBuilder(Constants.PB_SUGGESTIONS_TABLE_NAME).insert(data -> {
                    data.set("pb_server_id", c.getGuild().getId());
                    data.set("suggestion_message_id", v.getId());
                    data.set("suggester_discord_id", member.getId());
                });
            } catch (SQLException throwables) {
                MessageFactory.makeError(message, "Something went wrong in the database, please check with the developer.").queue();
                Xeus.getLogger().error("ERROR: ", throwables);
            }
        });
    }

    private boolean runClearAllChannelsFromDatabase(CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_channel_id", null);
                q.set("emoji_id", null);
                q.set("suggestion_community_channel_id", null);
                q.set("suggestion_approved_channel_id", null);
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
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }

        if (transformer.getSuggestionChannelId() == 0) {
            context.makeError("You want to set a approved suggestion channel, without the suggestions channel being set. Please set a \"Suggestion Channel\" with ``:command set-suggestions <channel> <emote>``").set("command", generateCommandTrigger(context.message)).queue();
            return false;
        }

        GuildChannel channel = MentionableUtil.getChannel(context.message, args, 1);
        if (channel == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }


        return updateApprovedSuggestionChannelInDatabase(transformer, context, (TextChannel) channel);
    }

    private boolean updateApprovedSuggestionChannelInDatabase(GuildSettingsTransformer transformer, CommandMessage context, TextChannel channel) {
        transformer.setSuggestionApprovedChannelId(channel.getIdLong());

        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_approved_channel_id", transformer.getSuggestionApprovedChannelId());
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
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }

        if (transformer.getSuggestionChannelId() == 0) {
            context.makeError("You want to set a community approved suggestion channel, without the suggestions channel being set. Please set a \"Suggestion Channel\" with ``:command set-suggestions <channel> <emote>``").set("command", generateCommandTrigger(context.message)).queue();
            return false;
        }

        GuildChannel channel = MentionableUtil.getChannel(context.message, args, 1);
        if (channel == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }


        return updateCommunityChannelInDatabase(transformer, context, (TextChannel) channel);
    }

    private boolean updateCommunityChannelInDatabase(GuildSettingsTransformer transformer, CommandMessage context, TextChannel channel) {
        transformer.setSuggestionCommunityChannelId(channel.getIdLong());

        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_community_channel_id", transformer.getSuggestionCommunityChannelId());
            });
            context.makeSuccess("Set the community channel to " + channel.getAsMention()).queue();
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    private boolean runSetSuggestionChannel(CommandMessage context, String[] args) {
        if (args.length < 2) {
            return sendErrorMessage(context, "Incorrect arguments");
        }
        GuildChannel c = MentionableUtil.getChannel(context.message, args, 1);
        if (c == null) {
            return sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
        }

        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return false;
        }

        transformer.setSuggestionChannelId(c.getIdLong());

        return updateChannelAndEmote(context, transformer.getSuggestionChannelId());
    }



    private boolean updateChannelAndEmote(CommandMessage context, long suggestionChannel) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("suggestion_channel_id", suggestionChannel);
            });

            context.makeSuccess("Suggestions have been enabled for <#:channelId> with the emote <:F::emoteId>").set("channelId", suggestionChannel).set("emoteId", context.getGuildSettingsTransformer().getEmojiId());
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
            return false;
        }

    }

    public static void createReactions(Message r) {
        r.addReaction(Emoji.fromFormatted("\uD83D\uDC4D")).queue();   // 👍
        r.addReaction(Emoji.fromFormatted("\uD83D\uDC4E")).queue();  // 👎
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
