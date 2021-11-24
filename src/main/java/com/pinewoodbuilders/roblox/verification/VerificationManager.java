package com.pinewoodbuilders.roblox.verification;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.kronos.TrellobanLabels;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.contracts.verification.VerificationResult;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.controllers.VerificationController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.VerificationTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.requests.service.group.GuildRobloxRanksService;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.roblox.verification.methods.VerificationMethodsManager;
import com.pinewoodbuilders.utilities.CacheUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VerificationManager {

    public final Cache <String, VerificationEntity> cache = CacheBuilder.newBuilder().recordStats()
        .expireAfterWrite(24, TimeUnit.HOURS).build();
    private final Xeus avaire;
    private final RobloxAPIManager manager;
    private final VerificationMethodsManager verificationMethodsManager;
    private final HashMap <Long, String> inVerification = new HashMap <>();

    public VerificationManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
        this.verificationMethodsManager = new VerificationMethodsManager(avaire, robloxAPIManager);
    }

    public VerificationMethodsManager getVerificationMethodsManager() {
        return verificationMethodsManager;
    }

    public boolean verify(CommandMessage context, boolean useCache) {
        return verify(context, context.member, useCache, context.guild);
    }

    public boolean verify(CommandMessage context, Member m) {
        return verify(context, m, true, context.guild);
    }

    public boolean verify(CommandMessage context) {
        return verify(context, context.member, true, context.guild);
    }

    public boolean verify(CommandMessage context, Member member, boolean useCache, Guild guild) {
        context.makeInfo("<a:loading:742658561414266890> Checking verification database <a:loading:742658561414266890>")
            .queue(originalMessage -> {
                VerificationResult vr = verify(context.getGuildSettingsTransformer(), member, guild, useCache);
                if (!vr.isSuccess()) {
                    originalMessage.editMessageEmbeds(context.makeError(vr.getMessage()).buildEmbed()).queue();
                } else {
                    originalMessage.editMessageEmbeds(context.makeSuccess(vr.getMessage()).buildEmbed()).queue();
                }
            });

        return false;
    }

    public VerificationResult verify(GuildSettingsTransformer transformer, Member member, Guild guild, boolean useCache) {
        if (member == null) {
            return new VerificationResult(false, "Member entity doesn't exist. Verification cancelled on you");
        }

        if (member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("Xeus Bypass"))) {
            return new VerificationResult(false,
                member.getAsMention() + " has the Xeus bypass role, this user cannot be verified/updated.");
        }

        VerificationEntity verificationEntity = fetchVerificationWithBackup(member.getId(), useCache);
        if (verificationEntity == null) {
            return new VerificationResult(false,
                "Xeus coudn't find your profile on the Xeus Database, please verify an account with `!verify`.");
        }

        if (transformer == null) {
            return new VerificationResult(false, "Xeus coudn't get the settings of this guild, please try again later.");
        }

        if (transformer.getPbVerificationTrelloban()) {
            HashMap <Long, List <TrellobanLabels>> trellobans = avaire.getRobloxAPIManager().getKronosManager().getTrelloBans();
            if (trellobans != null && runTrelloBan(transformer, member, guild, verificationEntity))
                return new VerificationResult(false, "User is Trellobanned...");
        }

        try {
            Collection accounts = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
                .where("roblox_user_id", verificationEntity.getRobloxId())
                .orWhere("roblox_username", verificationEntity.getRobloxUsername())
                .andWhere("main_group_id", transformer.getMainGroupId()).get();

            if (accounts.size() > 0) {
                if (transformer.getGlobalSettings() == null) {
                    return new VerificationResult(false, "Global settings cannot be read for the ANTI UNBAN Table, user is global-banned on this group ID. But global settings can't be loaded. Please contact the developer if this issue still persists");
                }


                if (avaire.getShardManager().getUserById(member.getId()) != null) {
                    String invite = getFirstInvite(guild);
                    avaire.getShardManager().getUserById(member.getId()).openPrivateChannel().flatMap(p -> p.sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(),
                        "*You have been **global-banned** from all discord that are connected to [this group](:groupLink) by an MGM Moderator. "
                            + "For the reason: *```" + accounts.get(0).getString("reason") + "```\n\n"
                            + "If you feel that your ban was unjustified please appeal at the group in question;"
                            + invite)
                        .setColor(Color.BLACK)
                        .set("groupLink",
                            "https://roblox.com/groups/" + transformer.getGlobalSettings().getMainGroupId())
                        .buildEmbed())).queue();
                }


                List<Guild> guilds = getGuildsByMainGroupId(avaire, transformer.getMainGroupId());

                int time = 0;
                String reason = "User has been global-banned from the MGM Bans database.";
                int bannedGuilds = 0;
                for (Guild guilde : guilds) {
                    if (guilde == null)
                        continue;
                    if (!guilde.getSelfMember().hasPermission(Permission.BAN_MEMBERS))
                        continue;

                    GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guilde);
                    if (settings.getGlobalBan()) {
                        if (settings.isOfficialSubGroup()) {
                            guild
                                .ban(member.getId(), time,
                                    "Banned by: " + member.getEffectiveName() + "\n" + "For: " + reason
                                        + "\n*THIS IS A MGM GLOBAL BAN, DO NOT REVOKE THIS BAN WITHOUT CONSULTING THE"
                                        + "MGM MODERATOR WHO INITIATED THE GLOBAL BAN, REVOKING THIS BAN WITHOUT MGM"
                                        + "APPROVAL WILL RESULT IN DISCIPlINARY ACTION!*")
                                .reason(
                                    "Global Ban, executed by " + member.getEffectiveName() + "." + "For: \n" + reason)
                                .queue();
                        } else {
                            guild.ban(member.getId(), time, "This is a global-ban that has been executed from"
                                + "the global ban list of the guild you're subscribed to... ").queue();
                        }
                        bannedGuilds++;
                    }
                }


                long mgmLogs = transformer.getGlobalSettings().getMgmLogsId();
                if (mgmLogs != 0) {
                    TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                    if (tc != null) {
                        tc.sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "[``:global-unbanned-id`` was auto-global-banned from all discords by :user for](:link):\n" + "```:reason```")
                            .set("global-unbanned-id", verificationEntity.getRobloxId())
                            .set("reason", accounts.get(0).getString("reason")).set("user", "XEUS AUTO BAN").buildEmbed()).queue();
                    }
                }

                if (transformer.getUserAlertsChannelId() != 0
                    && guild.getTextChannelById(transformer.getUserAlertsChannelId()) != null) {
                    MessageFactory
                        .makeEmbeddedMessage(guild.getTextChannelById(transformer.getUserAlertsChannelId()),
                            new Color(255, 0, 0))
                        .setThumbnail(member.getUser().getEffectiveAvatarUrl())
                        .setDescription(
                            "A global-banned user just tried to verify within this guild, user has been banned from all guilds and has been sent a message in DM's.")
                        .requestedBy(member).queue();

                }




                avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
                    .where("roblox_user_id", verificationEntity.getRobloxId())
                    .orWhere("roblox_username", verificationEntity.getRobloxUsername())
                    .update(p -> p.set("userId", member.getUser().getId()));
                long appealsDiscord = transformer.getGlobalSettings().getAppealsDiscordId();
                if (appealsDiscord != 0) {
                    if (!(guild.getIdLong() == appealsDiscord))
                        return new VerificationResult(false, "User has been global banned. Verification cancelled.");
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return new VerificationResult(false, "Something went wrong checking the MGM Anti-Unban table. Please check with the developer (`Stefano#7366`)");
        }

        if (isBlacklisted(guild, verificationEntity))
            return new VerificationResult(false, "Blacklisted on " + guild.getName());

        VerificationTransformer verificationTransformer = VerificationController.fetchVerificationFromGuild(avaire, guild);
        if (verificationTransformer == null) {
            return new VerificationResult(false,
                "The VerificationTransformer seems to have broken, please consult the developer of the bot.");
        }

        if (verificationTransformer.getNicknameFormat() == null) {
            return new VerificationResult(false, "The nickname format is not set (Wierd, it's the default but ok).");

        }

        if (verificationTransformer.getRanks() == null || verificationTransformer.getRanks().length() < 2) {
            return new VerificationResult(false, "Ranks have not been setup on this guild yet. Please ask the admins to setup the roles on this server.");

        }

        List <RobloxUserGroupRankService.Data> robloxRanks = manager.getUserAPI()
            .getUserRanks(verificationEntity.getRobloxId());
        if (robloxRanks == null || robloxRanks.size() == 0) {
            return new VerificationResult(false, verificationEntity.getRobloxUsername() + " does not have any ranks or groups on his name.");
        }

        GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) manager
            .toService(verificationTransformer.getRanks(), GuildRobloxRanksService.class);

        Map <GuildRobloxRanksService.GroupRankBinding, Role> bindingRoleMap = guildRanks.getGroupRankBindings()
            .stream()
            .collect(Collectors.toMap(Function.identity(),
                groupRankBinding -> guild.getRoleById(groupRankBinding.getRole()))),
            bindingRoleAddMap = new HashMap <>();

        // Loop through all the group-rank bindings
        bindingRoleMap.forEach((groupRankBinding, role) -> {
            List <String> robloxGroups = robloxRanks.stream()
                .map(data -> data.getGroup().getId() + ":" + data.getRole().getRank()).collect(Collectors.toList());

            for (String groupRank : robloxGroups) {
                String[] rank = groupRank.split(":");
                String groupId = rank[0];
                String rankId = rank[1];

                if (groupRankBinding.getGroups().stream().filter(group -> !group.getId().equals("GamePass")).anyMatch(
                    group -> group.getId().equals(groupId) && group.getRanks().contains(Integer.valueOf(rankId)))) {
                    bindingRoleAddMap.put(groupRankBinding, role);
                }

            }
        });

        bindingRoleMap.forEach((groupRankBinding, role) -> groupRankBinding.getGroups().stream().filter(data -> data.getId().equals("GamePass"))
            .map(pass -> avaire.getRobloxAPIManager().getUserAPI().getUserGamePass(verificationEntity.getRobloxId(),
                Long.parseLong(pass.getRanks().get(0).toString())))
            .filter(Objects::nonNull).forEach(pass -> bindingRoleAddMap.put(groupRankBinding, role)));

        // Collect the toAdd and toRemove roles from the previous maps
        java.util.Collection <Role> rolesToAdd = bindingRoleAddMap.values().stream()
            .filter(role -> PermissionUtil.canInteract(guild.getSelfMember(), role)).collect(Collectors.toList()),
            rolesToRemove = bindingRoleMap.values().stream().filter(role -> !bindingRoleAddMap.containsValue(role)
                && PermissionUtil.canInteract(guild.getSelfMember(), role)).collect(Collectors.toList());

        if (verificationTransformer.getVerifiedRole() != 0) {
            Role r = guild.getRoleById(verificationTransformer.getVerifiedRole());
            if (r != null) {
                rolesToAdd.add(r);
            }
        }

        StringBuilder stringBuilder = new StringBuilder();
        // Modify the roles of the member
        guild.modifyMemberRoles(member, rolesToAdd, rolesToRemove).queue(unused -> {
            stringBuilder.append("\n\n**Succesfully changed roles!**\n");
        }, throwable -> new VerificationResult(false, throwable.getMessage()));

        String rolesToAddAsString = "__**`" + member.getEffectiveName() + "`**__\nRoles to add:\n"
            + (rolesToAdd.size() > 0 ? (rolesToAdd.stream().map(role -> {
            if (role.hasPermission(Permission.BAN_MEMBERS)) {
                return "- **" + role.getName() + "**";
            } else {
                return "- " + role.getName();
            }
        }).collect(Collectors.joining("\n"))) : "No roles have been added");
        stringBuilder.append(rolesToAddAsString);

        String rolesToRemoveAsString = "\nRoles to remove:\n" + (bindingRoleMap.size() > 0
            ? (rolesToRemove.stream().map(role -> "- `" + role.getName() + "`").collect(Collectors.joining("\n")))
            : "No roles have been removed");
        // stringBuilder.append(rolesToRemoveAsString);

        if (!verificationEntity.getRobloxUsername().equals(member.getEffectiveName())) {
            if (PermissionUtil.canInteract(guild.getSelfMember(), member)) {
                guild.modifyNickname(member, verificationTransformer.getNicknameFormat()
                    .replace("%USERNAME%", verificationEntity.getRobloxUsername())).queue();
                stringBuilder.append("\n\nNickname has been set to `").append(verificationEntity.getRobloxUsername())
                    .append("`");
            } else {
                return new VerificationResult(false, "I do not have the permission to modify your nickname, or your highest rank is above mine.");
            }
        }

        return new VerificationResult(true, verificationEntity, stringBuilder.toString());
    }

    public List <Guild> getGuildsByMainGroupId(Xeus avaire, Long mainGroupId) throws SQLException {
        return getGuildsByMainGroupId(avaire, mainGroupId, false);
    }

    public List <Guild> getGuildsByMainGroupId(Xeus avaire, Long mainGroupId, boolean isOfficial) throws SQLException {
        if (!avaire.areWeReadyYet()) {
            return null;
        }
        Collection guildQuery = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
            .where("main_group_id", mainGroupId)
            .where(builder -> {if (isOfficial) {
                builder.where("official_sub_group", 1);
            }})
            .get();

        List <Guild> guildList = new LinkedList <>();
        for (DataRow dataRow : guildQuery) {
            Guild guild = avaire.getShardManager().getGuildById(dataRow.getString("id"));
            if (guild != null) {
                guildList.add(guild);
            }
        }

        return guildList;
    }

    private String getFirstInvite(Guild g) {
        List <Invite> invites = g.retrieveInvites().submit().getNow(null);
        if (invites == null || invites.size() < 1)
            return null;
        for (Invite i : invites) {
            return i.getUrl();
        }
        return null;
    }

    private boolean isBlacklisted(Guild guild, VerificationEntity verificationEntity) {
        switch (guild.getId()) {
            case "438134543837560832":
                return isBlacklisted(avaire.getBlacklistManager().getPBSTBlacklist(), guild, verificationEntity).isSuccess();
            case "572104809973415943":
                return isBlacklisted(avaire.getBlacklistManager().getTMSBlacklist(), guild, verificationEntity).isSuccess();
            case "498476405160673286":
                return isBlacklisted(avaire.getBlacklistManager().getPBMBlacklist(), guild, verificationEntity).isSuccess();
            case "436670173777362944":
                return isBlacklisted(avaire.getBlacklistManager().getPETBlacklist(), guild, verificationEntity).isSuccess();
            default:
                return false;
        }
    }

    private VerificationResult isBlacklisted(ArrayList <Long> blacklist, Guild guild, VerificationEntity verificationEntity) {
        if (blacklist.contains(verificationEntity.getRobloxId())) {
            String invite = getFirstInvite(guild);
            return new VerificationResult(true, "You're blacklisted on `" + guild.getName() + "`, access to the server has been denied.\n"
                + "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; "
                + invite);
        }
        return new VerificationResult(false, "Not blacklisted, continue...");
    }

    private boolean runTrelloBan(GuildSettingsTransformer transformer, Member member, Guild guild, VerificationEntity verificationEntity) {
        if (!avaire.getRobloxAPIManager().getKronosManager().getTrelloBans().containsKey(verificationEntity.getRobloxId())) {
            return false;
        }

        List <TrellobanLabels> banLabels = avaire.getRobloxAPIManager().getKronosManager().getTrelloBans()
            .get(verificationEntity.getRobloxId());
        if (banLabels.size() <= 0) {return false;}

        String canAppealRoleId = "834326360628658187";
        String trellobanRoleId = "875251061638701086";
        boolean canAppeal = true;
        boolean isPermenant = false;
        for (TrellobanLabels banLabel : banLabels) {
            if (banLabel.isPermban()) {
                isPermenant = true;
            }
            if (!banLabel.isAppealable()) {
                canAppeal = false;
            }
        }

        long appealsDiscord = transformer.getGlobalSettings().getAppealsDiscordId();

        if (!(guild.getIdLong() == appealsDiscord)) {
            if (canAppeal && isPermenant) {
                member.getUser().openPrivateChannel().flatMap(u -> u.sendMessage("Please open this message..."))
                    .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m, "You have been trello-banned forever within `"
                            + transformer.getGlobalSettings().getMainGroupName()
                            + "` however you are still allowed to appeal within the "
                            + (appealsDiscord != 0 ? guild.getName() : "Appeals guild not set") + ".\n\n"
                            + "Your trello-ban has the following labels, I'd suggest sharing these with your appeals handler:)"
                            + banLabels.stream().map(c -> "\n - " + c.getName()).collect(Collectors.joining()))
                            .buildEmbed()))
                    .queue();
            }

            if (canAppeal && !isPermenant) {
                member.getUser().openPrivateChannel().flatMap(u -> u.sendMessage("Please open this message..."))
                    .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m, "You have been trello-banned within`"
                            + transformer.getGlobalSettings().getMainGroupName()
                            + "` however you are still allowed to appeal within the "
                            + (appealsDiscord != 0 ? guild.getName() : "Appeals guild not set.\n\n")
                            + "Your trello-ban has the following labels, I'd suggest sharing these with your appeals handler:"
                            + banLabels.stream().map(c -> "\n - " + c.getName()).collect(Collectors.joining()))
                            .buildEmbed()))
                    .queue();

            }

            if (!canAppeal && isPermenant) {
                member.getUser().openPrivateChannel().flatMap(u -> u.sendMessage("Loading ban message..."))
                    .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m, "[You have been trello-banned forever within "
                            + transformer.getGlobalSettings().getMainGroupName()
                            + ", this ban is permanent, so you're not allowed to appeal it. We wish you a very good day sir/madam, and goodbye.](https://www.youtube.com/watch?v=BXUhfoUJjuQ)")
                            .buildEmbed()))
                    .queue();
                avaire.getShardManager().getTextChannelById("778853992704507945").sendMessage("Loading...")
                    .flatMap(mess -> mess.editMessage("Shuted the fuck up.")
                        .setEmbeds(MessageFactory.makeInfo(mess, member.getAsMention()
                            + " tried to verify in `" + guild.getName()
                            + "`. However, this person has a permenant trelloban to his name. He has been sent the STFU video (If his DM's are on) and have been global-banned.")
                            .buildEmbed()))
                    .queue();
            }
            long mgmLogs = transformer.getGlobalSettings().getMgmLogsId();
            if (mgmLogs != 0) {
                TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                if (tc != null) {
                    tc.sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(), "[``:global-unbanned-id`` has tried to verify in " + guild.getName()
                        + " but was trello banned, and has been global-banned. His labels are:](:link):\n"
                        + "```:reason```")
                        .set("global-unbanned-id", verificationEntity.getRobloxId())
                        .set("reason",
                            banLabels.stream().map(c -> "\n - " + c.getName()).collect(Collectors.joining()))
                        .set("user", "XEUS AUTO BAN").buildEmbed())
                        .queue();
                }
            }
            return true;
        }
        if (canAppeal) {
            guild.modifyMemberRoles(member, guild.getRoleById(canAppealRoleId))
                .queue();
            member.getUser().openPrivateChannel().flatMap(u -> u.sendMessage("Please open this message..."))
                .flatMap(m -> m.editMessage(member.getAsMention())
                    .setEmbeds(MessageFactory.makeSuccess(m, "You have been trello-banned within "
                        + transformer.getGlobalSettings().getMainGroupName()
                        + ", [however you are still allowed to appeal within the " + guild.getName() + "]().\n\n"
                        + "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:"
                        + banLabels.stream().map(c -> "\n - " + c.getName()).collect(Collectors.joining()))
                        .buildEmbed()))
                .queue();
        }

        if (!canAppeal && isPermenant) {
            guild.modifyMemberRoles(member, guild.getRoleById(trellobanRoleId))
                .queue();
            member.getUser().openPrivateChannel().flatMap(u -> u.sendMessage("Loading ban message..."))
                .flatMap(m -> m.editMessage(member.getAsMention()).setEmbeds(MessageFactory.makeSuccess(m,
                    "[You have been trello-banned forever within Pinewood, this ban is permenant, so you're not allowed to appeal it. We wish you a very good day sir, and goodbye.](https://www.youtube.com/watch?v=BXUhfoUJjuQ)")
                    .buildEmbed()))
                .queue();
            avaire.getShardManager().getTextChannelById("778853992704507945").sendMessage("Loading...")
                .flatMap(mess -> mess.editMessage("Shut the fuck up.")
                    .setEmbeds(new PlaceholderMessage(new EmbedBuilder(), member.getAsMention()
                        + " has a permanent trelloban. They have been sent the STFU video (if their DMs are on).")
                        .buildEmbed()))
                .queue();
        }

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
    public VerificationEntity fetchVerificationWithBackup(String discordUserId, boolean fromCache) {
        VerificationEntity entity = fetchVerificationFromDatabase(discordUserId, fromCache);
        if (entity == null) {
            entity = fetchVerificationFromRover(discordUserId, fromCache);
        }

        if (entity == null) {
            entity = fetchVerificationFromBloxlink(discordUserId, fromCache);
        }

        return entity;
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchInstantVerificationWithBackup(String discordUserId) {
        VerificationEntity entity = callUserFromDatabaseAPI(discordUserId);
        if (entity == null) {
            entity = callUserFromRoverAPI(discordUserId);
        }

        if (entity == null) {
            entity = callUserFromBloxlinkAPI(discordUserId);
        }

        return entity;
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerificationFromDatabase(String discordUserId, boolean fromCache) {
        if (!fromCache) {
            return forgetAndCache("pinewood:" + discordUserId);
        }
        try {
            return (VerificationEntity) CacheUtil.getUncheckedUnwrapped(cache, "pinewood:" + discordUserId,
                () -> callUserFromDatabaseAPI(discordUserId));
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        }
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerificationFromRover(String discordUserId, boolean fromCache) {
        if (!fromCache) {
            return forgetAndCache("rover:" + discordUserId);
        }
        try {
            return (VerificationEntity) CacheUtil.getUncheckedUnwrapped(cache, "rover:" + discordUserId,
                () -> callUserFromRoverAPI(discordUserId));
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        }
    }

    @Nullable
    @CheckReturnValue
    public VerificationEntity fetchVerificationFromBloxlink(String discordUserId, boolean fromCache) {
        if (!fromCache) {
            return forgetAndCache("bloxlink:" + discordUserId);
        }
        try {
            return (VerificationEntity) CacheUtil.getUncheckedUnwrapped(cache, "bloxlink:" + discordUserId,
                () -> callUserFromBloxlinkAPI(discordUserId));
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        }
    }

    @Nullable
    public VerificationEntity callUserFromBloxlinkAPI(String discordUserId) {
        Request.Builder request = new Request.Builder().addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://api.blox.link/v1/user/" + discordUserId);

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.has("primaryAccount")) {
                    VerificationEntity verificationEntity = new VerificationEntity(json.getLong("primaryAccount"),
                        manager.getUserAPI().getUsername(json.getLong("primaryAccount")), Long.valueOf(discordUserId),
                        "bloxlink");
                    cache.put("bloxlink:" + discordUserId, verificationEntity);
                    return verificationEntity;
                }
                return null;
            } else if (response.code() == 404) {
                return null;
            } else {
                throw new Exception("Rover API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private VerificationEntity forgetAndCache(String discordUserId) {
        if (cache.getIfPresent(discordUserId) != null) {
            cache.invalidate(discordUserId);
        }
        switch (discordUserId.split(":")[0]) {
            case "rover":
                return callUserFromRoverAPI(discordUserId.split(":")[1]);
            case "bloxlink":
                return callUserFromBloxlinkAPI(discordUserId.split(":")[1]);
            default:
                return callUserFromDatabaseAPI(discordUserId.split(":")[1]);
        }
    }

    public VerificationEntity callUserFromDatabaseAPI(String discordUserId) {
        try {
            Collection linkedAccounts = avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME)
                .where("id", discordUserId) /* .andWhere("main", "1") */.get();
            if (linkedAccounts.size() == 0) {
                return null;
            } else {
                VerificationEntity ve = new VerificationEntity(linkedAccounts.first().getLong("robloxId"),
                    manager.getUserAPI().getUsername(linkedAccounts.first().getLong("robloxId")), Long.valueOf(discordUserId),
                    "pinewood");
                cache.put("pinewood:" + discordUserId, ve);
                return ve;
            }
        } catch (SQLException throwables) {
            return null;
        }
    }

    @Nullable
    public VerificationEntity callUserFromRoverAPI(String discordUserId) {
        Request.Builder request = new Request.Builder().addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
            .url("https://verify.eryn.io/api/user/" + discordUserId);

        try (Response response = manager.getClient().newCall(request.build()).execute()) {
            if (response.code() == 200 && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());

                VerificationEntity verificationEntity = new VerificationEntity(json.getLong("robloxId"),
                    manager.getUserAPI().getUsername(json.getLong("robloxId")), Long.valueOf(discordUserId), "rover");

                cache.put(discordUserId, verificationEntity);
                return verificationEntity;
            } else if (response.code() == 404) {
                return null;
            } else {
                throw new Exception("Rover API returned something else then 200, please retry.");
            }
        } catch (IOException e) {
            Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public VerificationEntity callDiscordUserFromDatabaseAPI(Long robloxId) {
        try {
            Collection linkedAccounts = Xeus.getInstance().getDatabase()
                .newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME).where("robloxId", robloxId).get();
            if (linkedAccounts.size() == 0) {
                return null;
            } else {
                return new VerificationEntity(
                    linkedAccounts.first().getLong("robloxId"), Xeus.getInstance().getRobloxAPIManager().getUserAPI()
                    .getUsername(linkedAccounts.first().getLong("robloxId")),
                    linkedAccounts.first().getLong("id"), "pinewood");
            }
        } catch (SQLException throwables) {
            return null;
        }
    }

    public VerificationEntity callDiscordUserFromDatabaseAPI(String robloxId) {
        return callDiscordUserFromDatabaseAPI(Long.valueOf(robloxId));
    }

    private void errorMessage(String s, Message mess) {
        mess.editMessageEmbeds(
            new PlaceholderMessage(new EmbedBuilder(), s).setColor(MessageType.ERROR.getColor()).setTitle("Error during verification!").setTimestamp(Instant.now()).buildEmbed())
            .queue();
    }

    public HashMap <Long, String> getInVerification() {
        return inVerification;
    }

    private String getImageFromVerificationEntity(VerificationEntity ve) {
        if (ve == null) {
            return null;
        }
        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }
}
