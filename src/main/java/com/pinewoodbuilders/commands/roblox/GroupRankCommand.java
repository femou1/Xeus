package com.pinewoodbuilders.commands.roblox;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.requests.service.group.GroupRanksService;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import okhttp3.*;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class GroupRankCommand extends Command {
    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    public GroupRankCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Group Rank Command";
    }

    @Override
    public String getDescription() {
        return "Rank someone in the Roblox Group.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
                "`:command` - Ranking in the roblox Groups."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList(
                "`:command` - Ranking in the roblox Groups."
        );
    }


    @Override
    public List<String> getTriggers() {
        return Arrays.asList("group-rank", "gr");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(
                CommandGroups.MISCELLANEOUS
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
                "throttle:user,1,10",
                "isGuildLeadership"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {

        if (avaire.getConfig().getString("apiKeys.nobloxServerAPIKey") == null | avaire.getConfig().getString("apiKeys.nobloxServerAPIKey").length() < 1) {
            context.makeError("An noblox api key could not be found. Please enter it in the config.yml").requestedBy(context).queue();
            return false;
        }

        if (avaire.getConfig().getString("URL.noblox") == null | avaire.getConfig().getString("URL.noblox").length() < 1) {
            context.makeError("An noblox webserver could not be found. Please enter it in the config.yml").requestedBy(context).queue();
            return false;
        }

        if (context.getGuildTransformer() == null) {
            context.makeError("Transformer could not be loaded!").requestedBy(context).queue();
            return false;
        }

        if (context.getGuildSettingsTransformer().getRobloxGroupId() == 0) {
            context.makeError("The roblox ID of this group has not been set, please request a Facilitator or above to set this for you with `;rmanage`.").requestedBy(context).queue();
            return false;
        }

        Long contextId = getRobloxFromRoverId(context);
        if (contextId == null) {
            context.makeError("I coudn't find `:robloxName` on Roblox, please make sure you're verified with the correct account.").requestedBy(context).set("robloxName", context.getMember().getEffectiveName()).queue();
            return false;
        }
        Integer ownRank = returnOwnUserRank(context.getGuildSettingsTransformer().getRobloxGroupId(), contextId);

        if (ownRank < context.getGuildSettingsTransformer().getMinimumLeadRank()) {
            context.makeError("You are not allowed to rank someone in `" + context.getGuildSettingsTransformer().getRobloxGroupId() + "`. Please make sure you have the minimal rank ID of " + context.getGuildSettingsTransformer().getMinimumLeadRank()).queue();
            return false;
        }

        context.makeWarning("This command will allow you to rank someone in the configured group ID (``" + context.getGuildSettingsTransformer().getRobloxGroupId() + "``).").requestedBy(context).queue();
        Long botAccount = getRobloxId("PB_Xbot");
        Request.Builder request = new Request.Builder()
                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                .url("https://groups.roblox.com/v2/users/{userId}/groups/roles".replace("{userId}", botAccount.toString()));

        try (Response response = client.newCall(request.build()).execute()) {
            if (response.code() == 200) {
                RobloxUserGroupRankService grs = (RobloxUserGroupRankService) toService(response, RobloxUserGroupRankService.class);
                if (grs.hasData()) {
                    Optional<RobloxUserGroupRankService.Data> b = grs.getData().stream().filter(g -> g.getGroup().getId() == context.getGuildSettingsTransformer().getRobloxGroupId()).findFirst();
                    if (b.isEmpty()) {
                        context.makeError("PB_Xbot has not been found in the group linked to this server (`:groupId`). Please check if this is the correct group. And that PB_Xbot has the permission to rank in the group.").requestedBy(context).set("groupId", context.getGuildSettingsTransformer().getRobloxGroupId()).queue();
                        return false;
                    }

                    RobloxUserGroupRankService.Data d = b.get();

                    if (d.getRole().getId() >= context.getGuildSettingsTransformer().getMinimumLeadRank()) {
                        context.makeError("`PB_Xbot` does not have the correct minimum rank to promote someone in `:groupId`").requestedBy(context).set("groupId", context.getGuildSettingsTransformer().getRobloxGroupId()).queue();
                        return false;
                    }

                    if (avaire.getBotAdmins().getUserById(context.getMember().getId()).isGlobalAdmin()) {
                        context.makeInfo("**Rank**: `:rank` - `:rankId`\n")
                                .set("rankId", d.getRole().getId())
                                .set("rank", d.getRole().getName())
                                .setTitle(botAccount + " - PB_Xbot").requestedBy(context).queue();
                    }

                    context.makeInfo("What Roblox user would you like to edit?").requestedBy(context).queue(v -> {
                        avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, l -> {
                            return (l.getChannel().equals(context.getChannel()) &&
                                    l.getMember() != null && l.getMember().equals(context.getMember()) &&
                                    getRobloxId(l.getMessage().getContentRaw()) != null) ||
                                    l.getMessage().getContentRaw().equalsIgnoreCase("cancel");
                        }, k -> {
                            if (k.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                                context.makeInfo("Cancelled").queue();
                                return;
                            }


                            sendMessage(context, k, d.getRole().getRank(), ownRank);
                        });
                    });
                }
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        }


        return false;
    }

    private void sendMessage(CommandMessage context, GuildMessageReceivedEvent gme, int maxXeusRank, Integer ownRank) {
        String username = gme.getMessage().getContentRaw();
        Long id = getRobloxId(username);
        if (id == null) {
            context.makeError("I coudn't find `:robloxName` on Roblox, please check the name and try again.").requestedBy(context).set("robloxName", username).queue();
            return;
        }

        Integer rankeeRank = returnOwnUserRank(context.getGuildSettingsTransformer().getRobloxGroupId(), id);

        if (rankeeRank >= ownRank) {
            context.makeError("You're not able to rank someone who's the same rank as you, or higher!").queue();
            return;
        }


        GroupRanksService grs = avaire.getRobloxAPIManager().getGroupAPI().fetchGroupRanks(context.getGuildSettingsTransformer().getRobloxGroupId(), false);
        if (!(grs != null && grs.getRoles().size() > 0)) {
            context.makeError("Somehow, the group configured to this guild has no roles. Please check if the reality anchor is still running!").requestedBy(context).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (GroupRanksService.Role r : grs.getRoles()) {
            if (r.getRank() >= ownRank || r.getRank() >= maxXeusRank || r.getRank().equals(0)) {
                sb.append("`").append(r.getRank()).append("` - **").append(r.getName()).append("** - <:no:694270050257076304>\n");
            } else {
                sb.append("`").append(r.getRank()).append("` - **").append(r.getName()).append("** - <:yes:694268114803621908>\n");
            }
        }

        context.makeWarning("(**Username**: `:username` - **:rankName**)\n:ranks").setTitle(grs.getGroupId() + " - Roles").set("username", username).set("rankName", grs.getRoles().stream().filter(h -> {
            return h.getRank().equals(rankeeRank);
        }).findFirst()).set("ranks", sb.toString()).requestedBy(context).queue();
        context.makeInfo("What rank would you like to give this user? (Please use the number, not the name of the rank. Say `cancel` to cancel)").requestedBy(context).queue(v -> {
            avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class, l ->
                            l.getChannel().equals(context.getChannel()) &&
                                    l.getMember() != null &&
                                    l.getMember().equals(context.getMember()) &&
                                    NumberUtil.isNumeric(l.getMessage().getContentRaw()) ||
                                    l.getMessage().getContentRaw().equalsIgnoreCase("cancel"),
                    k -> {
                        if (k.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                            context.makeInfo("Cancelled").queue();
                            return;
                        }

                        if (Integer.parseInt(k.getMessage().getContentRaw()) == 0) {
                            context.makeError("You cannot rank someone to 0 (Guest), as this would exile them!").queue();
                            return;
                        }

                        if (Integer.parseInt(k.getMessage().getContentRaw()) >= ownRank) {
                            context.makeError("You're not able to rank someone higher then your own rank!").queue();
                            return;
                        }

                        if (Integer.parseInt(k.getMessage().getContentRaw()) >= maxXeusRank) {
                            context.makeError("You're not able to change the rank of " + username + " higher or the same rank as PB_Xbot").queue();
                            return;
                        }

                        makeConfirmMessage(context, context.getGuildSettingsTransformer().getRobloxGroupId(), id, username, grs.getRoles(), k.getMessage().getContentRaw());
                    });
        });


    }

    private Integer returnOwnUserRank(Long groupId, Long botAccount) {
        for (RobloxUserGroupRankService.Data s : avaire.getRobloxAPIManager().getUserAPI().getUserRanks(botAccount)) {
            if (s.getGroup().getId() == groupId) {
                return s.getRole().getRank();
            }
        }
        return 0;
    }

    private void makeConfirmMessage(CommandMessage context, long robloxGroupId, Long id, String username, List<GroupRanksService.Role> roles, String contentRaw) {
        Optional<GroupRanksService.Role> s = roles.stream().filter(k -> {
            return k.getRank().equals(Integer.valueOf(contentRaw));
        }).findFirst();

        if (!s.isPresent()) {
            context.makeError("Something went wrong, contact stefano.").queue();
            return;
        }
        GroupRanksService.Role rank = s.get();


        Button b1 = Button.success("yes:" + username, "Yes").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.secondary("no:" + username, "No").withEmoji(Emoji.fromUnicode("❌"));
        context.getChannel().sendMessageEmbeds(context.makeInfo("Are you sure you want to rank **:username** to **:rank** in `:robloxGroupId`?").requestedBy(context)
                .set("username", username).set("rank", rank.getName() + " (`" + rank.getRank() + "`)").set("robloxGroupId", robloxGroupId).buildEmbed()).setActionRow(b1, b2).queue(v -> {

            avaire.getWaiter().waitForEvent(ButtonClickEvent.class, r -> r.getChannel().equals(context.getChannel()) &&
                    r.getMember() != null &&
                    r.getMember().equals(context.getMember()), send -> {

                send.deferEdit().queue(m -> {
                    if (send.getButton().getEmoji().getName().equalsIgnoreCase("❌") || send.getButton().getEmoji().getName().equalsIgnoreCase("x")) {
                        context.makeWarning("Cancelled system, no rank has been changed.").queue();
                    } else if (send.getButton().getEmoji().getName().equalsIgnoreCase("✅")) {
                        Request.Builder request = new Request.Builder()
                                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                                .url(avaire.getConfig().getString("URL.noblox").replace("%location%", "SetRank"))
                                .post(RequestBody.create(json, buildPayload(id, robloxGroupId, rank.getRank())));

                        try (Response response = client.newCall(request.build()).execute()) {

                            if (response.code() == 500) {
                                context.makeError("Something went wrong when ranking " + username + "\n```json\n" + (response.body() != null ? response.body().string() : "RESPONSE BODY NOT RECEIVED") + "```").queue();
                            } else if (response.code() == 422) {
                                context.makeError("Something went wrong when ranking " + username + "\n```json\n" + (response.body() != null ? response.body().string() : "RESPONSE BODY NOT RECEIVED") + "```").queue();
                            } else if (response.code() == 200) {
                                context.makeSuccess("`:username`'s rank has been changed to: `:rank`\n\n```json\n:body\n```").set("username", username).set("rank", rank.getName()).set("body", response.body() != null ? response.body().string() : "NO RESPONSE RECIEVED").queue();

                                List<Member> member = context.getGuild().getMembersByEffectiveName(username, true);
                                if (member.size() > 0) {
                                    for (Member mem : member) {
                                        mem.getUser().openPrivateChannel().queue(l -> {
                                            l.sendMessageEmbeds(context.makeInfo("Your rank has been changed to `" + rank.getName() + "` in **" + context.getGuild().getName() + "**").buildEmbed()).queue();
                                        });
                                    }
                                }
                            } else {
                                context.makeError("Unkown error encountered: `" + response.code() + "`").queue();
                            }
                        } catch (IOException e) {
                            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
                        }

                    } else {
                        context.makeError("Invalid response received. Cancelled system, no rank has been changed.").queue();
                    }
                });
            }, 5, TimeUnit.MINUTES, () -> {
                context.makeError("You took to long to respond, please restart the report system!").queue();
            });

        });
    }

    private String buildPayload(long userId, long robloxId, int rankId) {
        JSONObject main = new JSONObject();

        main.put("auth_key", avaire.getConfig().getString("apiKeys.nobloxServerAPIKey"));
        main.put("Group", robloxId);
        main.put("Target", userId);
        main.put("Rank", rankId);

        return main.toString();
    }

    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return null;
        }
    }

    public Long getRobloxFromRoverId(CommandContext context) {
        VerificationEntity robloxUser = avaire.getRobloxAPIManager()
                .getVerification().fetchVerification(context.getMember().getId(), true);
        return robloxUser != null ? robloxUser.getRobloxId() : null;
    }

    public Object toService(Response response, Class<?> clazz) {
        return Xeus.gson.fromJson(toString(response), clazz);
    }

    public String toString(Response response) {
        try {
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    return body.string();
                }
            }
        } catch (IOException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
        return null;
    }

}
