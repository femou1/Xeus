package com.pinewoodbuilders.commands.roblox;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateHandbookCommand extends Command {
    public UpdateHandbookCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Update Handbook Command";
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("update-handbook", "uh");
    }
    boolean busy = false;

    /**
     * Gets the command description, this is used in help messages to help
     * users get a better understanding of what the command does.
     *
     * @return Never-null, the command description.
     */
    @Override
    public String getDescription(CommandContext context) {
        return "A command to update the handbook of the group connected to the server." +
            (getGroupId(context) != 0 ? "The ID used is: `" + getGroupId(context) + "`" : "");
    }

    /**
     * Gets an immutable list of middlewares that should be added to the command stack
     * before the command is executed, if the middleware that intercepts the
     * command message event fails the command will never be executed.
     *
     * @return An immutable list of command middlewares that should be invoked before the command.
     * @see com.pinewoodbuilders.contracts.middleware.Middleware
     */
    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isGuildLeadership",
            "throttle:user,1,3"
        );
    }

    private long getGroupId(@Nullable CommandContext context) {
        if (context == null) return 0;
        if (context.getGuildSettingsTransformer() == null) return 0;
        if (context.getGuildSettingsTransformer().getRobloxGroupId() == 0) return 0;
        return context.getGuildSettingsTransformer().getRobloxGroupId();
    }

    /**
     * Get the command priority, if a command is used via mentioning the bot and
     * the trigger used is shared with another command, the command with the
     * highest priority will be used.
     *
     * @return The command priority.
     */
    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.HIDDEN;
    }

    /**
     * The command executor, this method is invoked by the command handler
     * and the middleware stack when a user sends a message matching the
     * commands prefix and one of its command triggers.
     *
     * @param context The command message context generated using the
     *                JDA message event that invoked the command.
     * @param args    The arguments given to the command, if no arguments was given the array will just be empty.
     * @return true on success, false on failure.
     */

    Pattern idFinder = Pattern.compile("(\\d+)");
    Pattern uploadFinder = Pattern.compile("(upload://)");

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!(context.getGuildSettingsTransformer().getRobloxGroupId() == 645836)) {return false;}
        if (args.length == 0) {
            return sendErrorMessage(context, "What URL should be used to update the handbook?");
        }

        if (args[0].equals("set-post-id")) {
            return false;
        }

        context.makeWarning("""
                Welcome to the `DevForum Post Update` (DPU) system!
                Since I'm just been brought into existence, I'm in development.
                (Meaning anything can break unexpectedly)
                
                If something breaks, please contact the developer.
                           """).queue();

        // TODO: Find regex
        String url = args[0];
        Matcher m = idFinder.matcher(url);

        if (!m.find()) {return sendErrorMessage(context, "I was unable to find the ID of the post, please try again. If this continues to break, contact the developer.");}
        String id = m.group(0);
        HttpUrl urlParser = HttpUrl.parse(url);

        if (urlParser == null) {
            return sendErrorMessage(context, "I can't seem to be able to get the domain you requested. Please try again. Please copy the full URL.");
        }

        String apiKey = null;
        if (urlParser.host().equals("devforum.pinewood-builders.com")) {
            if (avaire.getConfig().getString("apiKeys.discourse.pinewood").length() < 1) {
                return sendErrorMessage(context, "API Key for the PB Devforum is not set, this request will not proceed.");
            };

            apiKey = avaire.getConfig().getString("apiKeys.discourse.pinewood");
        }



        long postId = getPostId(context, id, urlParser, apiKey);
        if (postId == 0) {return sendErrorMessage(context, "I seem to be unable to get the post ID, please ask the developer.");}

        JSONObject postInfo = getPostInformation(context, urlParser, postId, apiKey);
        if (postInfo == null) return sendErrorMessage(context, "Unable to get raw, contact dev.");

        String rawText = postInfo.getString("raw");
        Matcher matcher = uploadFinder.matcher(rawText);
        if (matcher.find()) {
            return sendErrorMessage(context, "<a:alerta:729735220319748117>, I FOUND "+matcher.groupCount()+" `upload://`'s IN THE RAW TEXT, PLEASE REPLACE ALL `upload://` WITH AN DIRECT IMAGE LINK (Imgur for example)");
        }

        String devForumUrl = "https://devforum.roblox.com/posts/"+ 7335071 +".json";
        HttpUrl devForumUrlParser = HttpUrl.parse(devForumUrl);

        String devForumApiKey = null;
        if (devForumUrlParser.host().equals("devforum.roblox.com")) {
            if (avaire.getConfig().getString("apiKeys.discourse.roblox").length() < 1) {
                return sendErrorMessage(context, "API Key for the PB Devforum is not set, this request will not proceed.");
            };

            devForumApiKey = avaire.getConfig().getString("apiKeys.discourse.roblox");
        }

        String finalDevForumApiKey = devForumApiKey;
        context.makeWarning("""
            Greetings Leaders of this group, a handbook change has been requested by :user...
            [Check the new version of the handbook here](:url)
            
            Do you approve of the changes? (Be sure to click on the :pencil2: to see the edits)
            
            The button will enable itself in 15 seconds.
            """).set("url", url).queue(message -> {
            try {
                waitForApproval(message, context, rawText, finalDevForumApiKey, devForumUrl, devForumUrlParser);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return true;
    }
    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");
    private void waitForApproval(Message message, CommandMessage context, String rawText, String finalDevForumApiKey, String devForumUrl, HttpUrl devForumUrlParser) throws InterruptedException {
        EmbedBuilder eb = new EmbedBuilder(message.getEmbeds().get(0));

        Button approveChanges = Button.secondary("approve:" + message.getId(), "Approve the changes");
        message.editMessageEmbeds(eb.build()).setActionRow(approveChanges.asEnabled()).queueAfter(15, TimeUnit.SECONDS);

        List<Long> approvals = new ArrayList <>();
        avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class,
            d -> isButton(d, message) && hasEnoughApprovals(approvals, context, d),
            d -> {
                Request.Builder request = new Request.Builder()
                    .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                    .put(RequestBody.create(buildPayload(rawText, context.getMember().getEffectiveName()), json))
                    .url(devForumUrl);

                assertLogins(devForumUrlParser, finalDevForumApiKey, request);

                try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
                    if (response.code() == 200) {
                        if (response.body()== null) return;
                        context.makeInfo("Handbook has been edited! Please check the handbook page to view the changes made to it. 'System' will soon update the images.").queue();
                    } else {
                        System.out.println(response.body().toString());
                        sendErrorMessage(context, "Something went wrong..." + (response.body() != null ? response.body().toString() : ""));
                        return;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private String buildPayload(String rawText, String effectiveName) {
        JSONObject main = new JSONObject();
        JSONObject post = new JSONObject();
        post.put("raw", rawText);
        post.put("edit_reason", "Handbook edited by '" + effectiveName + "' with approval of 3 additional leadership.");
        main.put("post", post);
        return main.toString();
    }


    private boolean hasEnoughApprovals(List <Long> approvals, CommandMessage context, ButtonInteractionEvent d) {
        d.deferEdit().queue();
        if (context.getAuthor().getId().equals(d.getMember().getId())) {
            return sendErrorMessage(context, "You can't approve your own changes dummy.");
        }

        if (approvals.contains(context.getAuthor().getIdLong())) {
            return sendErrorMessage(context, "You already approved the changes dummy.");
        }

        int level = XeusPermissionUtil.getPermissionLevel(context.getGuildSettingsTransformer(), context.guild, d.getMember()).getLevel();
        int minimalLevel = GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel();
        if (level < minimalLevel) {
            context.makeError("You do not have the correct permission level (LGL)").queue();
            return false;
        }
            approvals.add(d.getIdLong());
        context.makeSuccess(d.getMember().getEffectiveName() + " has approved the changes. (" + approvals.size() + "/3)").queue();
        return approvals.size() >= 3;
    }

    private boolean isButton(ButtonInteractionEvent interact, Message message) {
        return interact.getMessage().equals(message);
    }

    @Nullable
    private JSONObject getPostInformation(CommandMessage context, HttpUrl urlParser, long postId, String apiKey) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://" + urlParser.host() + "/posts/" + postId + ".json");

        assertLogins(urlParser, apiKey, request);

        try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                if (response.body()== null) return null;
                return new JSONObject(response.body().string());
            } else {
                sendErrorMessage(context, "Something went wrong..." + (response.body() != null ? response.body().toString() : ""));
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long getPostId(CommandMessage context, String id, HttpUrl urlParser, String apiKey) {
        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://" + urlParser.host() + "/t/"+ id +".json");

        assertLogins(urlParser, apiKey, request);

        try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
            if (response.code() == 200) {
                if (response.body()== null) return 0;
                JSONObject jsonResponse = new JSONObject(response.body().string());
                JSONObject postStream = jsonResponse.getJSONObject("post_stream");

                JSONArray posts = postStream.getJSONArray("posts");
                return ((JSONObject) posts.get(0)).getLong("id");

            } else {
                sendErrorMessage(context, "Something went wrong..." + (response.body() != null ? response.body().toString() : ""));
                return 0;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertLogins(HttpUrl urlParser, String apiKey, Request.Builder request) {
        if (apiKey != null && urlParser.host().equals("devforum.pinewood-builders.com")) {
            String[] login = apiKey.split("\\|");
            String username = login[0];
            String key = login[1];
            request.addHeader("Api-Username", username);
            request.addHeader("Api-Key", key);
        }

        if (apiKey != null && urlParser.host().equals("devforum.roblox.com")) {
            String[] login = apiKey.split("\\|");
            String username = login[0];
            String key = login[1];
            request.addHeader("Api-Username", username);
            request.addHeader("User-Api-Key", key);
        }
    }

    private boolean setPostId(CommandMessage context, String[] copyOfRange) {
        return false;
    }

    private boolean returnDefaultResponse(CommandMessage context) {
        return sendErrorMessage(context, """
            Please use a valid argument.
            `post-id` -> Set the POST ID used for the handbook ([devforum.roblox.com](https://devforum.roblox.com/))
            """);
    }
}
