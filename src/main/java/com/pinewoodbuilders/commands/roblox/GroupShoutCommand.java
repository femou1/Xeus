package com.pinewoodbuilders.commands.roblox;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import okhttp3.*;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GroupShoutCommand extends Command {
    public GroupShoutCommand(Xeus avaire) {
        super(avaire);
    }

    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public String getName() {
        return "Group Shout Command";
    }

    @Override
    public String getDescription() {
        return "Shouts in the Roblox Group.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Shouting in the roblox Groups."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Shouting in the roblox Groups."
        );
    }


    @Override
    public List<String> getTriggers() {
        return Arrays.asList("group-shout", "gs");
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
            "isGroupShoutOrHigher"
        );
    }
    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (avaire.getConfig().getString("apiKeys.nobloxServerAPIKey") == null | avaire.getConfig().getString("apiKeys.nobloxServerAPIKey").length() < 1) {
            context.makeError("An noblox api key could not be found. Please enter it in the config.yml").queue();
            return false;
        }

        if (avaire.getConfig().getString("URL.noblox") == null | avaire.getConfig().getString("URL.noblox").length() < 1) {
            context.makeError("An noblox webserver could not be found. Please enter it in the config.yml").queue();
            return false;
        }

        if (context.getGuildTransformer() == null) {
            context.makeError("Transformer could not be loaded!").queue();
            return false;
        }

        if (context.getGuildSettingsTransformer().getRobloxGroupId() == 0) {
            context.makeError("The roblox ID of this group has not been set, please request a Facilitator or above to set this for you with `;rmanage`.").queue();
            return false;
        }
        if (context.getGuildSettingsTransformer().getRobloxGroupId() == 0) {
            context.makeError("No group ID has been set for this guild!").queue();
            return false;
        }

        if (context.getGuildSettingsTransformer().getGroupShoutRoles().size() == 0) {
            context.makeError("No group shout roles have been set for this server, command has been disabled.").queue();
            return false;
        }
        context.makeWarning("PLEASE BE WARNED, THIS WILL SEND A GROUP SHOUT AS **PB_XBot** (If PB_XBot has permission to shout on ``"+context.getGuildSettingsTransformer().getRobloxGroupId()+"``, the group connected to the guild)").queue();



        context.makeInfo("What would you like to shout? (This sends instantly after you type it). Say ``cancel`` to cancel sending the message.").queue(v -> {
            avaire.getWaiter().waitForEvent(MessageReceivedEvent.class, l ->
                l.getChannel().equals(context.getChannel()) && l.getMember().equals(context.getMember()),
                k -> {
                if (k.getMessage().getContentRaw().equalsIgnoreCase("cancel")) {
                    context.makeInfo("Cancelled").queue();
                    return;
                }

                sendMessage(context, k);
            });
        });
        return false;
    }

    private void sendMessage(CommandMessage context, MessageReceivedEvent k) {
        String message = k.getMessage().getContentRaw();
        int charLimit = 255;

        charLimit = charLimit - context.getMember().getEffectiveName().length() + 2;
        message = context.getMember().getEffectiveName() + ": " + message;

        if (message.length() >= charLimit) {
            context.makeError("Sorry, but this message is to long to be sent to the group shout. (Roblox has a limit of 255 characters)").queue();
            return;
        }

        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url(avaire.getConfig().getString("URL.noblox").replace("%location%", "GroupShout"))
            .post(RequestBody.create(buildPayload(message, context.getGuildSettingsTransformer().getRobloxGroupId()), json));

        try (Response response = client.newCall(request.build()).execute()) {
            if (response.code() == 500) {
                context.makeError("[PB_Xbot doesn't have permissions to group shout to the group.](https://www.roblox.com/groups/:RobloxID). See response body here: \n```:message```").set("RobloxID", context.getGuildSettingsTransformer().getRobloxGroupId())
                    .set("message", response.body() != null ? response.body().string() : "[RESPONSE NOT FOUND/RECEIVED]").queue();
                return;
            }
            context.makeSuccess("Message sent to the [group wall](https://www.roblox.com/groups/:RobloxID): \n```" + response.body().string() + "```").set("RobloxID", context.getGuildSettingsTransformer().getRobloxGroupId()).queue();

            TextChannel tc = context.getGuild().getTextChannelById(context.getGuildSettingsTransformer().getAuditLogsChannelId());
            if (tc != null) {
                tc.sendMessageEmbeds(context.makeWarning("The following was sent to the [group](https://www.roblox.com/groups/:RobloxID) shout by **:memberAsMention**:\n```:message```")
                    .set("RobloxID", context.getGuildSettingsTransformer().getRobloxGroupId())
                    .set("message", message)
                    .set("memberAsMention", context.getMember().getAsMention()).buildEmbed()).queue();
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending sync with beacon request: " + e.getMessage());
        }
    }

    private String buildPayload(String contentRaw, long robloxId) {
        JSONObject main = new JSONObject();

        main.put("auth_key", avaire.getConfig().getString("apiKeys.nobloxServerAPIKey"));
        main.put("Group", robloxId);
        main.put("Message", contentRaw);

        return main.toString();
    }

}
