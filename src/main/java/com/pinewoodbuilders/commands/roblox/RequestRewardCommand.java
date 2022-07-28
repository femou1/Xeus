package com.pinewoodbuilders.commands.roblox;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RequestRewardCommand extends Command {


    public RequestRewardCommand(Xeus avaire) {
        super(avaire, false);
    }

    public static final Cache <Long, Guild> cache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build();

    @Override
    public String getName() {
        return "Request Reward Command";
    }

    @Override
    public String getDescription() {
        return "Request a reward for someone who did their job well in PBST.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Start the reward system."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Start the reward system."
        );
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("rr", "request-reward");
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
            "throttle:guild,1,30"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (true) {
            return false;
        }
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("Something went wrong loading the guild settings. Please try again later.").queue();
            return false;
        }
        if (args.length > 0) {
            if (XeusPermissionUtil.getPermissionLevel(context).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
                switch (args[0].toLowerCase()) {
                    case "sc", "set-channel" -> runSetRemittanceChannel(context, args);

                    case "clear", "reset" -> runClearAllChannelsFromDatabase(context);

                    default -> sendErrorMessage(context, "Invalid argument given.");

                }
            }
        }

        return startRewardWaiter(context, args);
    }

    private boolean startRewardWaiter(CommandMessage context, String[] args) {
        if (checkAccountAge(context)) {
            context.makeError("Sorry, but to prevent spam or abuse, only discord accounts older then 3 days may make a reward request.").queue();
            return false;
        }

        context.makeInfo("<a:loading:742658561414266890> Loading servers that are using recorded rewards...").queue(l -> {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where(consumer -> {
                consumer.where("request_reward_channel_id", "!=", null);
            }).orderBy("request_reward_channel_id");
            try {
                if (qb.get().size() == 0) {
                    context.makeError("No servers are using recorded rewards.").queue();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                qb.get().forEach(dataRow -> {
                    if (dataRow.getString("request_reward_channel_id") != null) {
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
                l.editMessageEmbeds(context.makeInfo("Welcome to the recorded reward request system. Please select the group you wanna reward someone in!\n\n" + sb.toString()).buildEmbed()).queue(
                    message -> {
                        avaire.getWaiter().waitForEvent(MessageReactionAddEvent.class, event -> {
                                return event.getMember().equals(context.member) && event.getMessageId().equalsIgnoreCase(message.getId());
                            }, react -> {

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

    private void runClearAllChannelsFromDatabase(CommandMessage context) {
        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("request_reward_channel_id", null);
            });

            context.makeSuccess("Any information about the remittance channels has been removed from the database.").queue();
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
        }

    }

    private void runSetRemittanceChannel(CommandMessage context, String[] args) {
        if (args.length < 3) {
            sendErrorMessage(context, "Incorrect arguments");
            return;
        }
        GuildChannel c = MentionableUtil.getChannel(context.message, args, 1);
        if (c == null) {
            sendErrorMessage(context, "Something went wrong please try again or report this to a higher up! (Channel)");
            return;
        }

        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            context.makeError("I can't pull the guilds information, please try again later.").queue();
            return;
        }
        updateChannelAndEmote(transformer, context, (TextChannel) c);
    }


    private void updateChannelAndEmote(GuildSettingsTransformer transformer, CommandMessage context, TextChannel channel) {
        transformer.setPatrolRemittanceChannel(channel.getIdLong());

        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId());
        try {
            qb.update(q -> {
                q.set("request_reward_channel_id", transformer.getRewardRequestChannelId());
            });

            context.makeSuccess("Remittance have been enabled for :channel").set("channel", channel.getAsMention()).queue();
        } catch (SQLException throwables) {
            context.makeError("Something went wrong in the database, please check with the developer. (Stefano#7366)").queue();
        }

    }


}
