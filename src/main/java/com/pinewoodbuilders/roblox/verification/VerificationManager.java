package com.pinewoodbuilders.roblox.verification;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.kronos.TrellobanLabels;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.VerificationTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.requests.service.group.GuildRobloxRanksService;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.roblox.verification.methods.VerificationMethodsManager;
import com.pinewoodbuilders.utilities.CacheUtil;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
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

  public final Cache < String, VerificationEntity > cache = CacheBuilder.newBuilder().recordStats()
    .expireAfterWrite(24, TimeUnit.HOURS).build();
  private final Xeus avaire;
  private final RobloxAPIManager manager;
  private final VerificationMethodsManager verificationMethodsManager;
  private final ArrayList < Guild > guildsList = new ArrayList < > ();
  private final HashMap < Long, String > inVerification = new HashMap < > ();

  public VerificationManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
    this.avaire = avaire;
    this.manager = robloxAPIManager;
    this.verificationMethodsManager = new VerificationMethodsManager(avaire, robloxAPIManager);
  }

  public VerificationMethodsManager getVerificationMethodsManager() {
    return verificationMethodsManager;
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
  public boolean verify(CommandMessage context, Member member, boolean useCache) {
    Guild guild = context.getGuild();
    context.makeInfo("<a:loading:742658561414266890> Checking verification database <a:loading:742658561414266890>")
      .queue(originalMessage -> {
        if (member == null) {
          errorMessage(context, "Member entity doesn't exist. Verification cancelled on you",
            originalMessage);
          return;
        }

        if (member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("Xeus Bypass"))) {
          errorMessage(context,
            member.getAsMention() +
            " has the Xeus bypass role, this user cannot be verified/updated.",
            originalMessage);
          return;
        }

        VerificationEntity verificationEntity = fetchVerificationWithBackup(member.getId(), useCache);
        if (verificationEntity == null) {
          errorMessage(context,
            "Xeus coudn't find your profile on the Pinewood Database, please verify an account with `!verify`.",
            originalMessage);
          return;
        }

        if (context.getGuildSettingsTransformer() == null) {
          errorMessage(context, "Xeus coudn't get the settings of this guild, please try again later.",
            originalMessage);
          return;
        }

        if (context.getGuildSettingsTransformer().getPbVerificationTrelloban()) {
          HashMap < Long, List < TrellobanLabels >> trellobans = avaire.getRobloxAPIManager()
            .getKronosManager().getTrelloBans();
          if (trellobans != null) {
            if (avaire.getRobloxAPIManager().getKronosManager().getTrelloBans()
              .containsKey(verificationEntity.getRobloxId())) {
              List < TrellobanLabels > banLabels = avaire.getRobloxAPIManager().getKronosManager()
                .getTrelloBans().get(verificationEntity.getRobloxId());
              if (banLabels.size() > 0) {
                String canAppealRoleId = "834326360628658187";
                String trellobanRoleId = "875251061638701086";
                boolean canAppeal = true;
                boolean isPermenant = false;
                for (TrellobanLabels banLabel: banLabels) {
                  if (banLabel.isPermban()) {
                    isPermenant = true;
                  }
                  if (!banLabel.isAppealable()) {
                    canAppeal = false;
                  }
                }

                if (!context.getGuild().getId().equals("750471488095780966")) {
                  if (canAppeal && isPermenant) {
                    member.getUser().openPrivateChannel()
                      .flatMap(u -> u.sendMessage("Please open this message..."))
                      .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m,
                            "You have been trello-banned forever within Pinewood, however you are still allowed to appeal within the PBAC.\n\n" +
                            "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:)" +
                            banLabels.stream()
                            .map(c -> "\n - " + c.getName())
                            .collect(Collectors.joining()))
                          .buildEmbed()))
                      .queue();
                  }

                  if (canAppeal && !isPermenant) {
                    member.getUser().openPrivateChannel()
                      .flatMap(u -> u.sendMessage("Please open this message..."))
                      .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m,
                            "You have been trello-banned within Pinewood, however you are still allowed to appeal within the PBAC.\n\n" +
                            "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:" +
                            banLabels.stream()
                            .map(c -> "\n - " + c.getName())
                            .collect(Collectors.joining()))
                          .buildEmbed()))
                      .queue();

                  }

                  if (!canAppeal && isPermenant) {
                    member.getUser().openPrivateChannel()
                      .flatMap(u -> u.sendMessage("Loading ban message..."))
                      .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m,
                            "[You have been trello-banned forever within Pinewood, this ban is permenant, so you're not allowed to appeal it. We wish you a very good day sir, and goodbye.](https://www.youtube.com/watch?v=BXUhfoUJjuQ)")
                          .buildEmbed()))
                      .queue();
                    avaire.getShardManager().getTextChannelById("778853992704507945")
                      .sendMessage("Loading...")
                      .flatMap(message -> message.editMessage("Shut the fuck up.")
                        .setEmbeds(MessageFactory.makeInfo(message, member
                            .getAsMention() + " tried to verify in `" +
                            context.guild.getName() +
                            "`. However, this person has a permenant trelloban to his name. He has been sent the STFU video (If his DM's are on) and have been global-banned.")
                          .buildEmbed()))
                      .queue();
                  }
                  TextChannel tc = avaire.getShardManager()
                    .getTextChannelById(Constants.PIA_LOG_CHANNEL);
                  if (tc != null) {
                    tc.sendMessageEmbeds(context
                        .makeInfo("[``:global-unbanned-id`` has tried to verify in " +
                          guild.getName() +
                          " but was trello banned, and has been global-banned. His labels are:](:link):\n" +
                          "```:reason```")
                        .set("global-unbanned-id", verificationEntity.getRobloxId())
                        .set("reason",
                          banLabels.stream().map(c -> "\n - " + c.getName())
                          .collect(Collectors.joining()))
                        .set("user", "XEUS AUTO BAN")
                        .set("link", context.getMessage().getJumpUrl()).buildEmbed())
                      .queue();
                  }
                  
                  return;
                } else {
                  if (canAppeal) {
                    context.guild.modifyMemberRoles(context.member,
                      context.guild.getRoleById(canAppealRoleId)).queue();
                    member.getUser().openPrivateChannel()
                      .flatMap(u -> u.sendMessage("Please open this message..."))
                      .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m,
                            "You have been trello-banned within Pinewood, [however you are still allowed to appeal within the PBAC]().\n\n" +
                            "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:" +
                            banLabels.stream()
                            .map(c -> "\n - " + c.getName())
                            .collect(Collectors.joining()))
                          .buildEmbed()))
                      .queue();
                  }

                  if (!canAppeal && isPermenant) {
                    context.guild.modifyMemberRoles(context.member,
                      context.guild.getRoleById(trellobanRoleId)).queue();
                    member.getUser().openPrivateChannel()
                      .flatMap(u -> u.sendMessage("Loading ban message..."))
                      .flatMap(m -> m.editMessage(member.getAsMention())
                        .setEmbeds(MessageFactory.makeSuccess(m,
                            "[You have been trello-banned forever within Pinewood, this ban is permenant, so you're not allowed to appeal it. We wish you a very good day sir, and goodbye.](https://www.youtube.com/watch?v=BXUhfoUJjuQ)")
                          .buildEmbed()))
                      .queue();
                    avaire.getShardManager().getTextChannelById("778853992704507945")
                      .sendMessage("Loading...")
                      .flatMap(message -> message.editMessage("Shut the fuck up.")
                        .setEmbeds(MessageFactory.makeInfo(message, member
                            .getAsMention() +
                            " has a permanent trelloban. They have been sent the STFU video (if their DMs are on).")
                          .buildEmbed()))
                      .queue();
                  }
                }
              }
            }
          }
        }

        try {
          Collection accounts = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
            .where("roblox_user_id", verificationEntity.getRobloxId())
            .orWhere("roblox_username", verificationEntity.getRobloxUsername())
            .andWhere("main_group_id", context.getGuildSettingsTransformer().getMainGroupId())
            .get();
          if (accounts.size() > 0) {
            GuildSettingsTransformer settingsTransformer = context.getGuildSettingsTransformer();
            if (context.getGlobalSettingsTransformer() == null) {
              context.makeError(
                  "The global settings could not be loaded, please try again later. Otherwise if this issue still persists, contact the developer!")
                .queue();
              return;
            }

            StringBuilder sb = new StringBuilder();

            Collection guilds = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
              .where("main_group_id", settingsTransformer.getMainGroupId()).get();

            int time = 0;
            String reason = "User has been global-banned from the MGM Bans database.";
            int bannedGuilds = 0;
            for (DataRow row: guilds) {
              Guild guilde = avaire.getShardManager().getGuildById(row.getString("id"));
              if (guilde == null)
                continue;
              if (!guilde.getSelfMember().hasPermission(Permission.BAN_MEMBERS))
                continue;
              if (context.getGlobalSettingsTransformer().getGlobalBan()) {
                if (row.getBoolean("official_sub_group", false)) {
                    guild.ban(member.getId(), time, "Banned by: " + context.member.getEffectiveName() +
                    "\n" + "For: " + reason
                    + "\n*THIS IS A MGM GLOBAL BAN, DO NOT REVOKE THIS BAN WITHOUT CONSULTING THE" +
                    "MGM MODERATOR WHO INITIATED THE GLOBAL BAN, REVOKING THIS BAN WITHOUT MGM"+
                    "APPROVAL WILL RESULT IN DISCIPlINARY ACTION!*")
                    .reason("Global Ban, executed by " + context.member.getEffectiveName() + "." +
                    "For: \n" + reason)
                    .queue();
                  sb.append("``").append(guild.getName()).append("`` - :white_check_mark:\n");
                } else {
                    guild.ban(member.getId(), time, "This is a global-ban that has been executed from" +
                    "the global ban list of the guild you're subscribed to... ")
                    .queue();
                }
                bannedGuilds++;
              } else {
                return;
              }
            }

            if (avaire.getShardManager().getUserById(member.getId()) != null) {
              String finalReason = reason;
              avaire.getShardManager().getUserById(member.getId()).openPrivateChannel().queue(p -> {
                p.sendMessageEmbeds(context.makeInfo(
                    "*You have been **global-banned** from all discord that are connected to [this group](:groupLink) by an MGM Moderator. " +
                    "For the reason: *```" + finalReason + "```\n\n" +
                    "If you feel that your ban was unjustified please appeal at the group in question;" +
                    "https://discord.gg/mWnQm25")
                  .setColor(Color.BLACK)
                  .set("groupLink",
                    "https://roblox.com/groups/" +
                    context.getGlobalSettingsTransformer().getMainGroupId())
                  .buildEmbed()).queue();
              });
            }

            TextChannel tc = avaire.getShardManager().getTextChannelById(Constants.PIA_LOG_CHANNEL);
            if (tc != null) {
              tc.sendMessageEmbeds(context.makeInfo(
                  "[``:global-unbanned-id`` was auto-global-banned from all discords by :user for](:link):\n" +
                  "```:reason```")
                .set("global-unbanned-id", verificationEntity.getRobloxId())
                .set("reason", accounts.get(0).getString("reason")).set("user", "XEUS AUTO BAN")
                .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
            }

            if (context.getGuildSettingsTransformer() != null) {
              GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
              if (transformer.getUserAlertsChannelId() != 0 && context.getGuild()
                .getTextChannelById(transformer.getUserAlertsChannelId()) != null) {
                MessageFactory
                  .makeEmbeddedMessage(context.getGuild().getTextChannelById(
                    transformer.getUserAlertsChannelId()), new Color(255, 0, 0))
                  .setThumbnail(context.getAuthor().getEffectiveAvatarUrl())
                  .setDescription(
                    "A global-banned user just tried to verify within this guild, user has been banned from all guilds and has been sent a message in DM's.")
                  .requestedBy(member).queue();
              }
            }

            if (context.getMember() != null) {
              member.getUser().openPrivateChannel().queue(p -> {
                try {
                  p.sendMessageEmbeds(context.makeInfo(
                      "*You have been **global-banned** from all the Pinewood Builders discords by an MGM Moderator. " +
                      "For the reason: *```" + accounts.get(0).getString("reason") +
                      "```\n\n" +
                      "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; " +
                      "https://discord.gg/mWnQm25")
                    .setColor(Color.BLACK).buildEmbed()).queue();
                } catch (ErrorResponseException e) {
                  originalMessage.editMessageEmbeds(context.makeInfo(
                      "*You have been **global-banned** from all the Pinewood Builders discords by an MGM Moderator. " +
                      "For the reason: ```" + accounts.get(0).getString("reason") +
                      "```\n\n" +
                      "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; " +
                      "https://discord.gg/mWnQm25")
                    .setColor(Color.BLACK).buildEmbed()).queue();
                }
              });
            }

            avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
              .where("roblox_user_id", verificationEntity.getRobloxId())
              .orWhere("roblox_username", verificationEntity.getRobloxUsername())
              .update(p -> p.set("userId", member.getUser().getId()));
            if (!context.getGuild().getId().equalsIgnoreCase("750471488095780966"))
              return;
          }
        } catch (SQLException throwables) {
          context.makeWarning(
              "Something went wrong checking the MGM Anti-Unban table. Please check with the developer (`Stefano#7366`)")
            .queue(k -> {
              k.delete().queueAfter(15, TimeUnit.SECONDS);
            });
          throwables.printStackTrace();
          return;
        }

        if (context.getGuild().getId().equals("438134543837560832")) {
          if (avaire.getBlacklistManager().getPBSTBlacklist()
            .contains(verificationEntity.getRobloxId())) {
            errorMessage(context, "You're blacklisted on PBST, access to the server has been denied.\n" +
              "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; " +
              "https://discord.gg/mWnQm25", originalMessage);
            return;
          }
        } else if (context.getGuild().getId().equals("572104809973415943")) {
          if (avaire.getBlacklistManager().getTMSBlacklist().contains(verificationEntity.getRobloxId())) {
            errorMessage(context, "You're blacklisted on TMS, access to the server has been denied.\n" +
              "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; " +
              "https://discord.gg/mWnQm25", originalMessage);
            return;
          }
        } else if (context.getGuild().getId().equalsIgnoreCase("498476405160673286")) {
          if (avaire.getBlacklistManager().getPBMBlacklist().contains(verificationEntity.getRobloxId())) {
            errorMessage(context, "You're blacklisted on PBM, access to the server has been denied.\n" +
              "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; " +
              "https://discord.gg/mWnQm25", originalMessage);
            return;
          }
        } else if (context.getGuild().getId().equalsIgnoreCase("436670173777362944")) {
          if (avaire.getBlacklistManager().getPETBlacklist().contains(verificationEntity.getRobloxId())) {
            errorMessage(context, "You're blacklisted on PET, access to the server has been denied.\n" +
              "If you feel that your ban was unjustified please appeal at the Pinewood Builders Appeal Center; " +
              "https://discord.gg/mWnQm25", originalMessage);
            return;
          }
        }

        VerificationTransformer verificationTransformer = context.getVerificationTransformer();
        if (verificationTransformer == null) {
          errorMessage(context,
            "The VerificationTransformer seems to have broken, please consult the developer of the bot.",
            originalMessage);
          return;
        }

        if (verificationTransformer.getNicknameFormat() == null) {
          errorMessage(context, "The nickname format is not set (Wierd, it's the default but ok).",
            originalMessage);
          return;
        }

        if (verificationTransformer.getRanks() == null || verificationTransformer.getRanks().length() < 2) {
          errorMessage(context,
            "Ranks have not been setup on this guild yet. Please ask the admins to setup the roles on this server.",
            originalMessage);
          return;
        }

        List < RobloxUserGroupRankService.Data > robloxRanks = manager.getUserAPI()
        .getUserRanks(verificationEntity.getRobloxId());
        if (robloxRanks == null || robloxRanks.size() == 0) {
          errorMessage(context, verificationEntity.getRobloxUsername() +
            " does not have any ranks or groups on his name.", originalMessage);
          return;
        }

        GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) manager
        .toService(verificationTransformer.getRanks(), GuildRobloxRanksService.class);

        Map < GuildRobloxRanksService.GroupRankBinding,
        Role > bindingRoleMap = guildRanks
        .getGroupRankBindings().stream()
        .collect(Collectors.toMap(Function.identity(),
          groupRankBinding -> guild.getRoleById(groupRankBinding.getRole()))),
        bindingRoleAddMap = new HashMap < > ();

        // Loop through all the group-rank bindings
        bindingRoleMap.forEach((groupRankBinding, role) -> {
          List < String > robloxGroups = robloxRanks.stream()
          .map(data -> data.getGroup().getId() + ":" + data.getRole().getRank())
          .collect(Collectors.toList());

          for (String groupRank: robloxGroups) {
            String[] rank = groupRank.split(":");
            String groupId = rank[0];
            String rankId = rank[1];

            if (groupRankBinding.getGroups().stream().filter(group -> !group.getId().equals("GamePass"))
              .anyMatch(group -> group.getId().equals(groupId) &&
                group.getRanks().contains(Integer.valueOf(rankId)))) {
              bindingRoleAddMap.put(groupRankBinding, role);
            }

          }
        });

        bindingRoleMap.forEach((groupRankBinding, role) -> {
          groupRankBinding.getGroups().stream().filter(data -> data.getId().equals("GamePass"))
          .map(pass -> avaire.getRobloxAPIManager().getUserAPI().getUserGamePass(
            verificationEntity.getRobloxId(),
            Long.parseLong(pass.getRanks().get(0).toString())))
          .filter(Objects::nonNull)
          .forEach(pass -> bindingRoleAddMap.put(groupRankBinding, role));
        });

        // Collect the toAdd and toRemove roles from the previous maps
        java.util.Collection < Role > rolesToAdd = bindingRoleAddMap.values().stream()
        .filter(role -> RoleUtil.canBotInteractWithRole(context.getMessage(), role))
        .collect(Collectors.toList()),
        rolesToRemove = bindingRoleMap.values().stream()
        .filter(role -> !bindingRoleAddMap.containsValue(role) &&
          RoleUtil.canBotInteractWithRole(context.getMessage(), role))
        .collect(Collectors.toList());

        if (verificationTransformer.getVerifiedRole() != 0) {
          Role r = context.getGuild().getRoleById(verificationTransformer.getVerifiedRole());
          if (r != null) {
            rolesToAdd.add(r);
          }
        }

        StringBuilder stringBuilder = new StringBuilder();
        // Modify the roles of the member
        guild.modifyMemberRoles(member, rolesToAdd, rolesToRemove).queue(unused -> {
          stringBuilder.append("\n\n**Succesfully changed roles!**\n");
        }, throwable -> context.makeError(throwable.getMessage()));

        String rolesToAddAsString = "__**`" + member.getEffectiveName() + "`**__\nRoles to add:\n" +
        (rolesToAdd.size() > 0 ? (rolesToAdd.stream().map(role -> {
          if (role.hasPermission(Permission.BAN_MEMBERS)) {
            return "- **" + role.getName() + "**";
          } else {
            return "- " + role.getName();
          }
        }).collect(Collectors.joining("\n"))) : "No roles have been added");
        stringBuilder.append(rolesToAddAsString);

        String rolesToRemoveAsString = "\nRoles to remove:\n" +
        (bindingRoleMap.size() > 0 ?
          (rolesToRemove.stream().map(role -> "- `" + role.getName() + "`")
            .collect(Collectors.joining("\n"))) :
          "No roles have been removed");
        // stringBuilder.append(rolesToRemoveAsString);

        if (!verificationEntity.getRobloxUsername().equals(member.getEffectiveName())) {
          if (PermissionUtil.canInteract(guild.getSelfMember(), member)) {
            context.getGuild().modifyNickname(member, verificationTransformer.getNicknameFormat()
              .replace("%USERNAME%", verificationEntity.getRobloxUsername())).queue();
            stringBuilder.append("\n\nNickname has been set to `")
              .append(verificationEntity.getRobloxUsername()).append("`");
          } else {
            context.makeError(
                "I do not have the permission to modify your nickname, or your highest rank is above mine.")
              .queue();
            stringBuilder.append("\n\nChanging nickname failed :(");
          }
        }
        originalMessage
        .editMessageEmbeds(context.makeSuccess(stringBuilder.toString())
          .setThumbnail(getImageFromVerificationEntity(verificationEntity)).buildEmbed())
        .queue();
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
            manager.getUserAPI().getUsername(json.getLong("primaryAccount")),
            Long.valueOf(discordUserId), "bloxlink");
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
        .where("id", discordUserId) /* .andWhere("main", "1") */ .get();
      if (linkedAccounts.size() == 0) {
        return null;
      } else {
        VerificationEntity ve = new VerificationEntity(linkedAccounts.first().getLong("robloxId"),
          manager.getUserAPI().getUsername(linkedAccounts.first().getLong("robloxId")),
          Long.valueOf(discordUserId), "pinewood");
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
          manager.getUserAPI().getUsername(json.getLong("robloxId")), Long.valueOf(discordUserId),
          "rover");

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
        return new VerificationEntity(linkedAccounts.first().getLong("robloxId"),
          Xeus.getInstance().getRobloxAPIManager().getUserAPI()
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

  private boolean errorMessage(CommandMessage em, String s, Message mess) {
    mess.editMessageEmbeds(em.makeError(s).setTitle("Error during verification!").requestedBy(em)
      .setTimestamp(Instant.now()).buildEmbed()).queue();

    return false;
  }

  public HashMap < Long, String > getInVerification() {
    return inVerification;
  }

  private boolean isTrelloBanned(VerificationEntity verificationEntity) {
    return avaire.getRobloxAPIManager().getKronosManager().getTrelloBans()
      .containsKey(verificationEntity.getRobloxId());
  }

  private String getImageFromVerificationEntity(VerificationEntity ve) {
    if (ve == null) {
      return null;
    }
    return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
  }
}