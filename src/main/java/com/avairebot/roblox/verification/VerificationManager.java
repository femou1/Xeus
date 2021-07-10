package com.avairebot.roblox.verification;

import com.avairebot.AppInfo;
import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.verification.VerificationEntity;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.transformers.VerificationTransformer;
import com.avairebot.requests.service.group.GuildRobloxRanksService;
import com.avairebot.requests.service.user.inventory.RobloxGamePassService;
import com.avairebot.requests.service.user.rank.RobloxUserGroupRankService;
import com.avairebot.roblox.RobloxAPIManager;
import com.avairebot.utilities.CacheUtil;
import com.avairebot.utilities.RoleUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.avairebot.utils.JsonReader.readJsonFromUrl;

public class VerificationManager {

    public final Cache<String, VerificationEntity> cache = CacheBuilder.newBuilder()
            .recordStats()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();
    private final AvaIre avaire;
    private final RobloxAPIManager manager;

    public VerificationManager(AvaIre avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
    }

    private static String getRobloxUsernameFromId(Long id) {
        try {
            JSONObject json = readJsonFromUrl("https://api.roblox.com/users/" + id);
            return json.getString("Username");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public boolean verify(CommandMessage context, boolean useCache) {
        return verify(context, context.member, useCache);
    }

    public boolean verify(CommandMessage context, Member m) {
        return verify(context, m, true);
    }

    public boolean verify(CommandMessage context) {
        return verify(context, context.member, true);
    }

    @SuppressWarnings("ConstantConditions")
    public boolean verify(CommandMessage commandMessage, Member member, boolean useCache) {
        Guild guild = commandMessage.getGuild();

        commandMessage.makeInfo("<a:loading:742658561414266890> Checking verification database <a:loading:742658561414266890>").queue(originalMessage -> {
            if (commandMessage.getMember() == null) {
                errorMessage(commandMessage, "Member entity doesn't exist. Verification cancelled on " + member.getEffectiveName(), originalMessage);
                return;
            }

            VerificationEntity verificationEntity = fetchVerification(member.getId(), useCache);
            if (verificationEntity == null) {
                errorMessage(commandMessage, "Xeus coudn't find your profile on the RoVer API. Please verify your account on https://verify.eryn.io/.", originalMessage);
                return;
            }

            if (commandMessage.getGuild().getId().equals("438134543837560832")) {
                if (avaire.getBlacklistManager().getPBSTBlacklist().contains(verificationEntity.getRobloxId())) {
                    errorMessage(commandMessage, "You're blacklisted on PBST, access to the server has been denied.", originalMessage);
                }
            } else if (commandMessage.getGuild().getId().equals("572104809973415943")) {
                if (avaire.getBlacklistManager().getTMSBlacklist().contains(verificationEntity.getRobloxId())) {
                    errorMessage(commandMessage, "You're blacklisted on TMS, access to the server has been denied.", originalMessage);
                }
            }

            VerificationTransformer verificationTransformer = commandMessage.getVerificationTransformer();
            if (verificationTransformer == null) {
                errorMessage(commandMessage, "The VerificationTransformer seems to have broken, please consult the developer of the bot.", originalMessage);
                return;
            }

            if (verificationTransformer.getNicknameFormat() == null) {
                errorMessage(commandMessage, "The nickname format is not set (Wierd, it's the default but ok).", originalMessage);
                return;
            }

            if (verificationTransformer.getRanks() == null || verificationTransformer.getRanks().length() < 2) {
                errorMessage(commandMessage, "Ranks have not been setup on this guild yet. Please ask the admins to setup the roles on this server.", originalMessage);
                return;
            }

            List<RobloxUserGroupRankService.Data> robloxRanks = manager.getUserAPI().getUserRanks(verificationEntity.getRobloxId());
            if (robloxRanks == null || robloxRanks.size() == 0) {
                errorMessage(commandMessage, verificationEntity.getRobloxUsername() + " does not have any ranks or groups on his name.", originalMessage);
                return;
            }

            GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) manager.toService(verificationTransformer.getRanks(), GuildRobloxRanksService.class);

            Map<GuildRobloxRanksService.GroupRankBinding, Role> bindingRoleMap =
                    guildRanks.getGroupRankBindings().stream()
                            .collect(Collectors.toMap(Function.identity(), groupRankBinding -> guild.getRoleById(groupRankBinding.getRole()))),
                    bindingRoleAddMap = new HashMap<>();


            //Loop through all the group-rank bindings
            bindingRoleMap.forEach((groupRankBinding, role) -> {
                List<String> robloxGroups = robloxRanks.stream().map(data -> data.getGroup().getId() + ":" + data.getRole().getRank())
                        .collect(Collectors.toList());

                for (String groupRank : robloxGroups) {
                    String[] rank = groupRank.split(":");
                    String groupId = rank[0];
                    String rankId = rank[1];

                    if (groupRankBinding.getGroups().stream()
                            .filter(group -> !group.getId().equals("GamePass"))
                            .anyMatch(group -> group.getId().equals(groupId) && group.getRanks().contains(Integer.valueOf(rankId)))) {
                        bindingRoleAddMap.put(groupRankBinding, role);
                    }

                }
            });

            bindingRoleMap.forEach((groupRankBinding, role) -> {
                List<String> gamepassBinds = groupRankBinding.getGroups().stream().map(data -> data.getId() + ":" + data.getRanks().get(0))
                        .collect(Collectors.toList());

                for (String groupRank : gamepassBinds) {
                    // Loop through all the gamepass-bindings
                    String[] rank = groupRank.split(":");
                    String rankId = rank[1];
                    gamepassBinds.stream().filter(group -> group.split(":")[0].equals("GamePass") && group.split(":")[1].equals(rankId)).forEach(gamepass -> {
                        List<RobloxGamePassService.Datum> rgs = manager.getUserAPI().getUserGamePass(verificationEntity.getRobloxId(), Long.valueOf(rankId));
                        if (rgs != null) {
                            bindingRoleAddMap.put(groupRankBinding, role);
                        }
                    });

                }
            });

            //Collect the toAdd and toRemove roles from the previous maps
            java.util.Collection<Role> rolesToAdd = bindingRoleAddMap.values().stream().filter(role -> RoleUtil.canBotInteractWithRole(commandMessage.getMessage(), role)).collect(Collectors.toList()),
                    rolesToRemove = bindingRoleMap.values()
                            .stream().filter(role -> !bindingRoleAddMap.containsValue(role) && RoleUtil.canBotInteractWithRole(commandMessage.getMessage(), role)).collect(Collectors.toList());

            StringBuilder stringBuilder = new StringBuilder();
            //Modify the roles of the member
            guild.modifyMemberRoles(member, rolesToAdd, rolesToRemove)
                    .queue(unused -> {
                        stringBuilder.append("\n\n**Succesfully changed roles!**");
                    }, throwable -> commandMessage.makeError(throwable.getMessage()));

            String rolesToAddAsString = "\nRoles to add:\n" + (rolesToAdd.size() > 0
                    ? (rolesToAdd.stream().map(role -> "- `" + role.getName() + "`")
                    .collect(Collectors.joining("\n"))) : "No roles have been added");
            stringBuilder.append(rolesToAddAsString);

            String rolesToRemoveAsString = "\nRoles to remove:\n" + (bindingRoleMap.size() > 0
                    ? (rolesToRemove.stream().map(role -> "- `" + role.getName() + "`")
                    .collect(Collectors.joining("\n"))) : "No roles have been removed");
            stringBuilder.append(rolesToRemoveAsString);
            originalMessage.editMessageEmbeds(commandMessage.makeSuccess(stringBuilder.toString()).buildEmbed()).queue();

            if (!verificationEntity.getRobloxUsername().equals(member.getEffectiveName())) {
                if (PermissionUtil.canInteract(guild.getSelfMember(), member)) {
                    commandMessage.getGuild().modifyNickname(member, verificationTransformer.getNicknameFormat().replace("%USERNAME%", verificationEntity.getRobloxUsername())).queue();
                    stringBuilder.append("Nickname has been set to `").append(verificationEntity.getRobloxUsername()).append("`");
                } else {
                    commandMessage.makeError("I do not have the permission to modify your nickname, or your highest rank is above mine.").queue();
                    stringBuilder.append("Changing nickname failed :(");
                }
            }
        });

        return false;
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerification(String discordUserId, boolean fromCache) {
        return fetchVerification(discordUserId, fromCache, null);
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerification(String discordUserId, boolean fromCache, @Nullable String selectedApi) {
        switch (selectedApi != null ? selectedApi : "pinewood") {
            case "bloxlink":
                return fetchVerificationFromBloxlink(discordUserId, fromCache);
            case "rover":
                return fetchVerificationFromRover(discordUserId, fromCache);
            case "pinewood":
            default:
                return fetchVerificationFromDatabase(discordUserId, fromCache);
        }
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerificationFromDatabase(String discordUserId, boolean fromCache) {
        if (!fromCache) {
            return forgetAndCache(discordUserId);
        }
        try {
            return (VerificationEntity) CacheUtil.getUncheckedUnwrapped(cache, discordUserId, () -> callUserFromDatabaseAPI(discordUserId));
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        }
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerificationFromRover(String discordUserId, boolean fromCache) {
        if (!fromCache) {
            return forgetAndCache(discordUserId);
        }
        try {
            return (VerificationEntity) CacheUtil.getUncheckedUnwrapped(cache, discordUserId, () -> callUserFromRoverAPI(discordUserId));
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        }
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerificationFromBloxlink(String discordUserId, boolean fromCache) {
        if (!fromCache) {
            return forgetAndCache(discordUserId);
        }
        try {
            return (VerificationEntity) CacheUtil.getUncheckedUnwrapped(cache, discordUserId, () -> callUserFromBloxlinkAPI(discordUserId));
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        }
    }

    @Nullable
    public VerificationEntity callUserFromBloxlinkAPI(String discordUserId) {
        Request.Builder request = new Request.Builder()
                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                .url("https://api.blox.link/v1/user/" + discordUserId);

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                HashMap<String, String> responseJson = AvaIre.gson.fromJson(response.body().string(), new TypeToken<HashMap<String, String>>() {
                }.getType());
                VerificationEntity verificationEntity = new VerificationEntity(Long.valueOf(responseJson.get("primaryAccount")), getRobloxUsernameFromId(Long.valueOf(responseJson.get("primaryAccount"))), Long.valueOf(discordUserId),  "bloxlink");

                cache.put(discordUserId, verificationEntity);
                return verificationEntity;
            } else if (response.code() == 404) {
                return null;
            } else {
                throw new Exception("Rover API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            AvaIre.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private VerificationEntity forgetAndCache(String discordUserId) {
        if (cache.getIfPresent(discordUserId) != null) {
            cache.invalidate(discordUserId);
            return callUserFromDatabaseAPI(discordUserId);
        }
        return callUserFromDatabaseAPI(discordUserId);
    }

    public VerificationEntity callUserFromDatabaseAPI(String discordUserId) {
        try {
            Collection linkedAccounts = avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME).where("id", discordUserId).get();
            if (linkedAccounts.size() == 0) {
                return null;
            } else {
                return new VerificationEntity(linkedAccounts.first().getLong("robloxId"), linkedAccounts.first().getString("username"), Long.valueOf(discordUserId), "pinewood");
            }
        } catch (SQLException throwables) {
            return null;
        }
    }

    @Nullable
    public VerificationEntity callUserFromRoverAPI(String discordUserId) {
        Request.Builder request = new Request.Builder()
                .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                .url("https://verify.eryn.io/api/user/" + discordUserId);

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                HashMap<String, String> responseJson = AvaIre.gson.fromJson(response.body().string(), new TypeToken<HashMap<String, String>>() {
                }.getType());
                VerificationEntity verificationEntity = new VerificationEntity(Long.valueOf(responseJson.get("robloxId")), responseJson.get("robloxUsername"), Long.valueOf(discordUserId), "rover");

                cache.put(discordUserId, verificationEntity);
                return verificationEntity;
            } else if (response.code() == 404) {
                return null;
            } else {
                throw new Exception("Rover API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            AvaIre.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean errorMessage(CommandMessage em, String s, Message mess) {
        mess.editMessage(em.makeError(s).setTitle("Error during verification!").requestedBy(em).setTimestamp(Instant.now()).buildEmbed()).queue();
        return false;
    }

}