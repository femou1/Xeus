package com.pinewoodbuilders.commands.roblox;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.contracts.roblox.assets.Asset;
import com.pinewoodbuilders.contracts.roblox.assets.AssetType;
import com.pinewoodbuilders.utilities.RandomUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UniformCommand extends Command {

    public UniformCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "uniform";
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("uniform", "uni");
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
        return "A command to update the uniforms of the group connected to the server." +
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
            "throttle:user,1,5"
        );
    }

    private long getGroupId(CommandContext context) {
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


    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (busy) {
            return sendErrorMessage(context, "This command is currently sending requests to Roblox. Please wait 10 minutes and try again.");
        }
        if (getGroupId(context) == 0) {return sendErrorMessage(context, "You need to set a Roblox group ID first. Please configure the group ID before continue-ing");}
        if (getGroupId(context) != 645836) {return sendErrorMessage(context, "This command currently only works for `645836` (**Pinewood Builders Security Team**), contact the dev to see if your group can use it.");}

        if (args.length == 0) {
            return sendErrorMessage(context, "You need to specify a command. `:command uniform update` to update the group, `:command uniform reset` to reset the group.");
        }


        // Switch the first argument to lowercase.
        // Check if the first argument is either enable, disable or reset.
        return switch (args[0].toLowerCase()) {
            case "enable" -> enableUniform(context, Arrays.copyOfRange(args, 1, args.length));
            case "disable" -> disableUniform(context, Arrays.copyOfRange(args, 1, args.length));
            case "reset" -> resetUniform(context, Arrays.copyOfRange(args, 1, args.length));
            default -> sendErrorMessage(context, """
                You need to specify a command.
                - `:command enable <type>` to enable a list of uniforms.
                - `:command disable <type>` to disable a list of uniforms.
                - `:command reset (type)` to reset a list of uniforms, or all of them.
                """);
        };
    }

    private boolean resetUniform(CommandMessage context, String[] args) {
        if (args.length == 0) {
            List <Asset> assets = new java.util.ArrayList <>(Arrays.stream(Asset.values()).toList());
            Collections.sort(assets);

            return updateUniforms(context, assets, true, true);
        }

        AssetType assetType = Arrays.stream(AssetType.values()).filter(type -> type.name().equalsIgnoreCase(args[0])).findFirst().orElse(AssetType.NULL);
        List<Asset> assets = Arrays.stream(Asset.values()).filter(asset -> asset.getAssetType() == assetType).toList();

        if (!assets.isEmpty()) {
            return updateUniforms(context, assets, true, true);
        } else {
            return sendErrorMessage(context, "Invalid asset type. Possible types: " +
                Arrays.stream(AssetType.values()).map(type -> "\n- `" + type.name().toUpperCase() + "`").collect(Collectors.joining()));
        }
    }

    private boolean disableUniform(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "You need to specify a type. `:command disable <type>` to enable a list of uniforms. Possible types: " +
                Arrays.stream(AssetType.values()).map(type -> "\n- `" + type.name().toUpperCase() + "`").collect(Collectors.joining()));
        }

        AssetType assetType = Arrays.stream(AssetType.values()).filter(type -> type.name().equalsIgnoreCase(args[0])).findFirst().orElse(AssetType.NULL);
        List<Asset> assets = Arrays.stream(Asset.values()).filter(asset -> asset.getAssetType() == assetType).toList();

        if (!assets.isEmpty()) {
            return updateUniforms(context, assets, false, false);
        } else {
            return sendErrorMessage(context, "Invalid asset type. Possible types: " +
                Arrays.stream(AssetType.values()).map(type -> "\n- `" + type.name().toUpperCase() + "`").collect(Collectors.joining()));
        }
    }

    private boolean enableUniform(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "You need to specify a command. `:command enable <type>` to enable a list of uniforms. Possible types: " +
                Arrays.stream(AssetType.values()).map(type -> "\n- `" + type.name().toUpperCase() + "`").collect(Collectors.joining()));
        }

        AssetType assetType = Arrays.stream(AssetType.values()).filter(type -> type.name().equalsIgnoreCase(args[0])).findFirst().orElse(AssetType.NULL);
        List<Asset> assets = Arrays.stream(Asset.values()).filter(asset -> asset.getAssetType() == assetType).toList();

        if (!assets.isEmpty()) {
            return updateUniforms(context, assets, true, false);
        } else {
            return sendErrorMessage(context, "Invalid asset type. Possible types: " +
                Arrays.stream(AssetType.values()).map(type -> "\n- `" + type.name().toUpperCase() + "`").collect(Collectors.joining()));
        }
    }


    private boolean updateUniforms(CommandMessage context, List <Asset> assets, boolean forSale, boolean reset) {
        busy = true;
        Bucket bucket = Bucket.builder()
            .addLimit(Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(1))))
            .build();

        int count = 0;
        int total = assets.size();
        for (Asset asset : assets) {
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                try {
                    long timeUntilRefill = probe.getNanosToWaitForReset();
                    long refillTime = timeUntilRefill != 0L ? timeUntilRefill : 0;
                    long durationInMs = TimeUnit.MILLISECONDS.convert(refillTime, TimeUnit.NANOSECONDS);
                    long durationInSeconds = TimeUnit.SECONDS.convert(refillTime, TimeUnit.NANOSECONDS);

                    context.makeWarning("You are being rate limited. Please wait `" + durationInMs + "ms` (`"+durationInSeconds+"s` (`"+timeUntilRefill+"ns`)) before using this command again.").queue(l -> l.delete().queueAfter(durationInSeconds, TimeUnit.SECONDS));
                    TimeUnit.SECONDS.sleep((long) (durationInSeconds + (count*0.2)));
                } catch (InterruptedException ignored) {
                    busy = false;
                }
            }

            count++;
            if (!updateAsset(context, forSale, reset, asset, bucket, count, total)) {
                busy = false;
                break;
            }
        }

        context.getMessageChannel().sendMessage(context.member.getAsMention())
            .setEmbeds(
                context.makeSuccess("Updated " + count + " of " + total + " assets.")
                    .requestedBy(context)
                    .buildEmbed())
            .queue();
        busy = false;
        return false;
    }

    private boolean updateAsset(CommandMessage context, boolean forSale, boolean reset, Asset asset, Bucket bucket, int count, int total) {
        String s = buildPayload(asset.getAssetId(),
            asset.getName(),
            asset.getDescription() + "\nUID: " + RandomUtil.generateString(RandomUtil.getInteger(1) + 2),
            asset.isComments(),
            reset ? asset.isForSale() : forSale,
            asset.getRobloxPrice()
        );

        RequestBody body = RequestBody.create(s,
            json);


        Request.Builder request = new Request.Builder()
            .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url(avaire.getConfig().getString("URL.noblox").replace("%location%", "UniformUpdate"))
            .post(body);


        try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
            if (response.code() == 500) {
                context.makeError("Something went wrong, please check the noblox console.").queue();
                return false;
            }
            if (response.code() == 429) {
                context.makeError("Rate-limit reached, waiting an additional 60 seconds...").queue(l -> l.delete().queueAfter(10, TimeUnit.SECONDS));
                TimeUnit.SECONDS.sleep(10);
                updateAsset(context, forSale, reset, asset, bucket, count, total);
                return true;
            }

            if (response.code() == 422) {
                context.makeError("""
                    ```json
                        :jsonError
                    ```""").set("jsonError", response.body().string()).queue();
                context.makeError("""
                    ```json
                        :jsonError
                    ```""").set("jsonError", s).queue();
                return false;
            }

            if (response.code() == 200) {
                JSONObject newAsset = new JSONObject(response.body().string());

                context.makeSuccess("""
                    Successfully updated:
                        `:assetName`.
                    Price: `:price`
                    Description: ```:description```
                    Type: `:type`
                    For Sale: `:forSale`
                    """)
                    .setTitle("Asset Updated {"+asset.getAssetId()+"} - Assets: " + count + "/" + total, "https://www.roblox.com/catalog/" + newAsset.get("id"))
                    .setFooter("Remaining Tokens: " + bucket.getAvailableTokens())
                    .setTimestamp(Instant.now())
                    .setColor(getColor(newAsset.getString("error")))
                    .set("assetName", newAsset.get("name"))
                    .set("price", asset.getRobloxPrice())
                    .set("description", asset.getDescription())
                    .set("type", asset.getAssetType().toString())
                    .set("forSale", reset ? asset.isForSale() : forSale)
                    .queue(l -> l.delete().queueAfter(1, TimeUnit.MINUTES));
                TimeUnit.SECONDS.sleep(60);
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending sync with beacon request: " + e.getMessage());
            busy = false;
        } catch (InterruptedException e) {
            busy = false;
            throw new RuntimeException(e);
        }
        return true;
    }

    private Color getColor(String error) {
        return switch (error.toLowerCase()) {
            case "ok" -> MessageType.SUCCESS.getColor();
            case "warning" -> MessageType.WARNING.getColor();
            default -> MessageType.INFO.getColor();
        };

    }

    private static final MediaType json = MediaType.parse("application/json; charset=utf-8");
    private String buildPayload(long assetId, String name, String description, boolean enableComments, boolean onSale, int price) {
        JSONObject main = new JSONObject();

        main.put("auth_key", avaire.getConfig().getString("apiKeys.nobloxServerAPIKey"));
        main.put("assetId", assetId);
        main.put("name", name);
        main.put("description", description);
        main.put("enableComments", enableComments);
        main.put("sellForRobux", onSale ? price : 0);
        main.put("genreSelection", "All");



        return main.toString();
    }

}
