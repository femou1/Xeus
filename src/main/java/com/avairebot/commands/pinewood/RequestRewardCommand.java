package com.avairebot.commands.pinewood;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.blacklist.features.FeatureScope;
import com.avairebot.chat.PlaceholderMessage;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.contracts.commands.CommandGroup;
import com.avairebot.contracts.commands.CommandGroups;
import com.avairebot.factories.RequestFactory;
import com.avairebot.requests.Request;
import com.avairebot.requests.Response;
import com.avairebot.requests.service.user.rank.RobloxUserGroupRankService;
import com.avairebot.utilities.NumberUtil;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RequestRewardCommand extends Command {


    public RequestRewardCommand(AvaIre avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Request Reward Command";
    }

    @Override
    public String getDescription() {
        return "Request a reward for someone who did their job well in PBST.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Start the reward system."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Start the reward system."
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("rr", "request-reward");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.REPORTS
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isOfficialPinewoodGuild",
            "throttle:guild,1,30"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!context.getAuthor().getId().equals("173839105615069184")) {
            context.makeError("[This command is temporarly disabled due changed in the recent way rewards are done.](https://discord.com/channels/438134543837560832/459764670782504961/848609525586067457)").queue();
            return true;
        }


        if (avaire.getFeatureBlacklist().isBlacklisted(context.getAuthor(), 438134543837560832L, FeatureScope.REWARD_REQUESTS)) {
            context.makeError("You have been blacklisted from creating reward requests for this guild. Please ask a **Level 4** or higher to remove you from the ``"+ context.getGuild().getName()   +"`` Reward request blacklist. (Or global, if you're globally banned from all features)").queue();
            return false;
        }

        long requesterId = getRobloxId(context.getMember().getEffectiveName());
        if (requesterId == 0L) {
            context.makeError("Sorry, but your username seems to not exist inside roblox. Please make sure your nick is correct.").queue();
            return false;
        }

        String requesterName = context.getMember().getEffectiveName();

        Request requesterRequest = RequestFactory.makeGET("https://groups.roblox.com/v1/users/" + requesterId + "/groups/roles");
        requesterRequest.send((Consumer<Response>) response -> {
            if (response.getResponse().code() == 200) {
                RobloxUserGroupRankService grs = (RobloxUserGroupRankService) response.toService(RobloxUserGroupRankService.class);
                Optional<RobloxUserGroupRankService.Data> b = grs.getData().stream().filter(l -> l.getGroup().getId() == 645836).findFirst();

                if (b.isPresent()) {
                    //context.makeInfo(AvaIre.gson.toJson(b.get().getRole())).queue();
                    if (args.length < 1) {
                        context.makeError("Please add who you'd like to request a reward for in the command.\n\n**Example**: \n - ``" + generateCommandPrefix(context.getMessage()) + "rr <Roblox Username>``").queue();
                        return;
                    }

                    String requestedName = args[0];
                    long requestedId = getRobloxId(requestedName);
                    if (requestedId == 0L) {
                        context.makeError("Sorry, but your username you gave us, does not exist on roblox.").queue();
                        return;
                    }


                    Request requestedRequest = RequestFactory.makeGET("https://groups.roblox.com/v1/users/" + requestedId + "/groups/roles");
                    requestedRequest.send((Consumer<Response>) response2 -> {
                        if (response2.getResponse().code() == 200) {
                            RobloxUserGroupRankService grs2 = (RobloxUserGroupRankService) response2.toService(RobloxUserGroupRankService.class);
                            Optional<RobloxUserGroupRankService.Data> b2 = grs2.getData().stream().filter(l -> l.getGroup().getId() == 645836 && l.getGroup().getOwner().getUsername().equals("Diddleshot")).findFirst();

                            if (b2.isPresent()) {
                                sendSpecialRanksAndRolesMessage(context, requestedId, requestedName, requesterName, b, b2);

                            } else {
                                context.makeInfo(String.valueOf(response2.getResponse().code())).queue();
                                makeErrorMessage(context, "The user who you've requested a reward for isn't in that group, please check if this is correct or not.");
                            }
                        }
                    });

                } else {
                    context.makeInfo(String.valueOf(response.getResponse().code())).queue();
                    makeErrorMessage(context, "You're not in PBST, make sure you are. If you are, try the command again or contact ``Stefano#7366``");
                }

            }
        });
        return true;
    }

    private void makeErrorMessage(CommandMessage context, String s) {
        context.makeError(s).requestedBy(context).queue(d -> {
            d.delete().queueAfter(20, TimeUnit.SECONDS);
            //context.message.delete().queueAfter(20, TimeUnit.SECONDS);
        });
    }

    private static boolean checkEvidenceAcceptance(CommandMessage context, GuildMessageReceivedEvent pm) {
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
            pm.getChannel().sendMessage(context.makeError("Sorry, but we are only accepting [YouTube links](https://www.youtube.com/upload), [Gyazo Links](https://gyazo.com), [LightShot Links](https://app.prntscr.com/), [Discord Image Links](https://cdn.discordapp.com/attachments/689520756891189371/733599719351123998/unknown.png) or [Imgur links](https://imgur.com/upload) as evidence. Try again (Do not restart the reward system, I'm still listening).").buildEmbed()).queue();
            return false;
        }
        return true;
    }

    private void sendSpecialRanksAndRolesMessage(CommandMessage context, long requestedId, String requestedName, String requesterName, Optional<RobloxUserGroupRankService.Data> rUs, Optional<RobloxUserGroupRankService.Data> rU) {

        RobloxUserGroupRankService.Data requestedUser = rU.get();
        RobloxUserGroupRankService.Data requesterUser = rUs.get();


        context.makeWarning("The user you requested has the rank: " + requestedUser.getRole().getName() + "\n" +
            "You have the rank: " + requesterUser.getRole().getName() + "\n\n" +
            "You can " + (requestedUser.getRole().getRank() < requesterUser.getRole().getRank() ? "ACTUALLY" : "NOT") + "request a reward for this person.").queue();

        if (requesterUser.getRole().getRank() < requestedUser.getRole().getRank() || requestedUser.getRole().getRank() == requesterUser.getRole().getRank()) {
            context.makeError("Sorry, but you're not allowed to request a reward for this rank.").queue();
            return;
        }


        context.makeInfo("You're able to request a reward for this rank. How many points do you think this person should earn?").queue(
            message -> {
                avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, ignored ->
                    ignored.getChannel().equals(message.getChannel()) && ignored.getMember().equals(context.member), points -> {
                    String amount = points.getMessage().getContentRaw();
                    if (NumberUtil.isNumeric(amount)) {
                        context.makeWarning("You're requesting ``" + amount + "`` points for: **" + requestedName + "**").queue();
                        context.makeInfo("What's the reason for the reward? Did he do something good? Or why do you think he/she should earn ``" + amount + "`` points for being a good `" + requestedUser.getRole().getName() + "`?")
                            .queue(ignored2 -> avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, ignored0 ->
                                ignored0.getChannel().equals(message.getChannel()) && ignored0.getMember().equals(context.member), reason -> {
                                context.makeWarning("You'd like to give ``:name`` (:rank). For the reason: \n" + reason.getMessage().getContentRaw() + "\n\nDo you have any proof that this happened?\n" +
                                    "Please enter a **LINK** to evidence.\n\n" +
                                    "**We're accepting**:\n" +
                                    "- [YouTube Links](https://www.youtube.com/upload)\n" +
                                    "- [Imgur Links](https://imgur.com/upload)\n" +
                                    "- [Discord Image Links](https://cdn.discordapp.com/attachments/689520756891189371/733599719351123998/unknown.png)\n" +
                                    "- [Gyazo Links](https://gyazo.com)\n" +
                                    "- [LightShot Links](https://app.prntscr.com/)\n" +
                                    "- [Streamable](https://streamable.com)\n" +
                                    "If you want a link/video/image service added, please ask ``Stefano#7366``").set("name", requestedName).set("rank", requestedUser.getRole().getName()).queue(
                                    ignored1 -> avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, event ->
                                        event.getChannel().equals(message.getChannel()) && event.getMember().equals(context.member) && checkEvidenceAcceptance(context, event), evidence -> {
                                        context.makeWarning("Ok, let me collect everything here...").queue();
                                        context.makeInfo(
                                            "**User**: ``:requestedUser`` \n" +
                                                "**Rank**: ``:rank`` \n" +
                                                "**Requested points**: ``:points`` \n" +
                                                "**Evidence**: :evidence \n" +
                                                "**Reason**: :reason\n\n" +
                                                "Please confirm if you want to send this as the reward request.")
                                            .set("requestedUser", requestedName)
                                            .set("rank", requestedUser.getRole().getName())
                                            .set("points", points.getMessage().getContentRaw())
                                            .set("evidence", evidence.getMessage().getContentRaw())
                                            .set("reason", reason.getMessage().getContentRaw()).queue(
                                            confirmMessage -> {
                                                confirmMessage.addReaction("\uD83D\uDC4D").queue();
                                                confirmMessage.addReaction("\uD83D\uDC4E").queue();
                                                avaire.getWaiter().waitForEvent(GuildMessageReactionAddEvent.class, event -> event.getMember().equals(context.member) && event.getChannel().equals(context.channel) && event.getMessageId().equals(confirmMessage.getId()) && isValidReaction(context, event), confirmReaction -> {
                                                    if (confirmReaction.getReactionEmote().getName().equals("\uD83D\uDC4D")) {

                                                        PlaceholderMessage eb = context.makeWarning("**User**: ``:requestedUser`` \n" +
                                                            "**Rank**: ``:rank`` \n" +
                                                            "**Requested points**: ``:points`` \n" +
                                                            "**Evidence**: :evidence \n" +
                                                            "**Reason**: \n:reason\n\n")
                                                            .set("requestedUser", requestedName)
                                                            .set("rank", requestedUser.getRole().getName())
                                                            .set("points", points.getMessage().getContentRaw())
                                                            .set("evidence", evidence.getMessage().getContentRaw())
                                                            .set("reason", reason.getMessage().getContentRaw())
                                                            .setTitle(requestedName, "https://www.roblox.com/users/" + requestedId + "/profile")
                                                            .setFooter(requesterName, context.getAuthor().getEffectiveAvatarUrl())
                                                            .setTimestamp(Instant.now());

                                                        if (context.getGuild().getMembersByEffectiveName(requestedName, true).size() > 0) {
                                                            eb.setThumbnail("https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&username=" + requestedName);
                                                        }

                                                        avaire.getShardManager().getTextChannelById(Constants.REWARD_REQUESTS_CHANNEL_ID)
                                                            .sendMessage(eb.buildEmbed()).queue(
                                                            msg -> {
                                                                context.makeSuccess("[I've made your reward request! It can be found in the PBST Discord! Click on this message to jump to the message](:link)")
                                                                    .set("link", msg.getJumpUrl()).queue();
                                                                msg.addReaction("\uD83D\uDC4D").queue();
                                                                msg.addReaction("\uD83D\uDC4E").queue();
                                                                msg.addReaction("✅").queue();
                                                                msg.addReaction("❌").queue();
                                                                msg.addReaction("🚫").queue();
                                                                msg.addReaction("\uD83D\uDD04").queue();
                                                            }
                                                        );
                                                    } else {


                                                        context.makeSuccess("Cancelled the reward!").queue();
                                                    }
                                                }, 30, TimeUnit.SECONDS, () -> context.makeError("No response received after 30 seconds the reward system has been stopped.").queue());
                                            }
                                        );

                                    }, 120, TimeUnit.SECONDS, () -> context.makeError("No response received after 120 seconds, the reward system has been stopped.").queue())
                                );

                            }, 240, TimeUnit.SECONDS, () -> context.makeError("No response received after 240 seconds, the reward system has been stopped.").queue()));
                    } else {
                        makeErrorMessage(context, "Sorry, but you have entered something else then a number. Please restart the reward system. Give a numeral point amount to grant a player.");
                    }
                }, 60, TimeUnit.SECONDS, () -> context.makeError("No response received after 60 seconds, the reward system has been stopped.").queue());
            }

        );
    }

    private boolean isValidReaction(CommandMessage context, GuildMessageReactionAddEvent event) {
        if (event.getReactionEmote().getName().equals("\uD83D\uDC4D") || event.getReactionEmote().getName().equals("\uD83D\uDC4E")) {
            return true;
        } else {
            context.makeError(context.member.getAsMention() + "\nPlease react with either a valid emoji (\uD83D\uDC4D or \uD83D\uDC4E)").queue();
            return false;
        }
    }

    private boolean checkBypass(int requester) {
        return requester == 31 || requester == 32;
    }

    private boolean isValidRobloxian(CommandMessage context, int userId) {
        if (userId == 0) {
            context.makeError("Invalid username.").queue();
            return false;
        } else {
            return true;
        }
    }

    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return 0L;
        }
    }
}
