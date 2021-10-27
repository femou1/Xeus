package com.pinewoodbuilders.commands.roblox.verification;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.kronos.TrellobanLabels;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.controllers.VerificationController;
import com.pinewoodbuilders.database.transformers.VerificationTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.requests.service.group.GuildRobloxRanksService;
import com.pinewoodbuilders.requests.service.user.inventory.RobloxGamePassService;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.utilities.CheckPermissionUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.RoleUtil;
import com.pinewoodbuilders.utilities.menu.Paginator;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.internal.utils.PermissionUtil;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static javassist.util.proxy.ProxyFactory.useCache;

public class UpdateCommand extends Command {
    private final Paginator.Builder builder;

    public UpdateCommand(Xeus avaire) {
        super(avaire);
        builder = new Paginator.Builder().setColumns(1).setFinalAction(m -> {
            try {
                m.clearReactions().queue();
            } catch (PermissionException ignore) {
            }
        }).setItemsPerPage(10).waitOnSinglePage(false).useNumberedItems(false).showPageNumbers(true).wrapPageEnds(true)
            .setEventWaiter(avaire.getWaiter()).setTimeout(5, TimeUnit.MINUTES);
    }

    @Override
    public String getName() {
        return "Update Command";
    }

    @Override
    public String getDescription() {
        return "Update a user in the discord.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList("`:command <user>` - Update a user in the discord.");
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList("`:command @Stefano#7366`");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList("isGuildHROrHigher");
    }

    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("update");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeError("Who/whom would you like to verify?").queue();
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "everyone":
                return updateEveryone(context);
            case "all-guilds":
                return updateAllGuilds(context);
            default:
                return updateMember(context, args);
        }
    }

    private boolean updateMember(CommandMessage context, String[] args) {
        User u = MentionableUtil.getUser(context, args);
        if (u != null) {
            Member m = context.guild.getMember(u);
            if (m != null) {
                VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification()
                    .fetchInstantVerificationWithBackup(u.getId());
                if (verificationEntity != null) {
                    return avaire.getRobloxAPIManager().getVerification().verify(context, m);
                } else {
                    context.makeError(
                        "This user is not verified in any database. Please ask him/her to verify with `!verify`")
                        .queue();
                }
            } else {
                context.makeError("Member not found.").queue();
            }
        } else {
            context.makeError("User not found.").queue();
        }
        return false;
    }

    boolean globalVerificationRunning;
    boolean verificationRunning;
    private final Bandwidth limit = Bandwidth.simple(240, Duration.ofSeconds(60));
    private final Bucket bucket = Bucket.builder().addLimit(limit).build();

    private boolean updateEveryone(CommandMessage context) {
        int level = CheckPermissionUtil.getPermissionLevel(context).getLevel();
        if (level < CheckPermissionUtil.GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel()) {
            context.makeError("You got to be MGM or above to run this command.").queue();
            return false;
        }

        // if (context.getGuild().getVerificationLevel().equals(VerificationLevel.VERY_HIGH)) {

        // }

        if (globalVerificationRunning) {
            context.makeError("A pinewood wide verification is already running. Please try again later...").queue();
            return false;
        }

        if (verificationRunning) {
            context.makeError(
                "A guild is already running a mass verification, due to rate-limits. You might get rate-limited.")
                .queue();
        }

        context.makeWarning(
            "Running verification...\nDepending on the member total (:members) this might take a while. You will be mentioned once the time is over.")
            .set("members", context.getGuild().getMembers().size()).queue();
        AtomicInteger count = new AtomicInteger();


        HashMap <Long, List <TrellobanLabels>> trellobans = null;

        if (context.getGuildSettingsTransformer().getPbVerificationTrelloban()) {
            long mgi = context.getGuildSettingsTransformer().getMainGroupId();
            if (context.getGuildSettingsTransformer().getMainGroupId() != 0) {
                trellobans = avaire.getRobloxAPIManager().getKronosManager()
                    .getTrelloBans(mgi);
            }
        }

        verificationRunning = true;
        HashMap <Member, String> ignoredMembers = new HashMap <>();
        ArrayList <Guild> guilde = new ArrayList <>();
        for (String s : guilds) {
            Guild g = avaire.getShardManager().getGuildById(s);
            if (g != null) {
                guilde.add(g);
            }
        }
        for (Member member : context.getGuild().getMembers()) {
            if (!bucket.tryConsume(1)) {
                context.makeInfo("Hit bucket ratelimit, pausing for 30 seconds")
                    .setFooter("This message deletes after 25 seconds.")
                    .queue(m -> m.delete().queueAfter(25, TimeUnit.SECONDS));
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ignored) {

                }
            }

            if (member == null) {
                continue;
            }

            if (!PermissionUtil.canInteract(context.guild.getSelfMember(), member)) {
                // ignoredMembers.put(member, "Cannot interact with user");
                continue;
            }

            if (member.getRoles().stream().anyMatch(
                r -> r.getName().equalsIgnoreCase("Xeus Bypass") || r.getName().equalsIgnoreCase("RoVer Bypass"))) {
                ignoredMembers.put(member, " has the Xeus/RoVer bypass role, this user cannot be verified/updated.");
                continue;
            }

            VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification()
                .fetchVerificationWithBackup(member.getId(), useCache);
            if (verificationEntity == null) {
                ignoredMembers.put(member, "Xeus coudn't find this profile anywhere, user was not verified");
                continue;
            }

            if (trellobans != null) {
                if (trellobans
                    .containsKey(verificationEntity.getRobloxId())) {
                    List <TrellobanLabels> banLabels = trellobans
                        .get(verificationEntity.getRobloxId());

                    if (banLabels.size() > 0) {
                        if (context.getMessage().getContentDisplay().contains("--pbac-trelloban-message")) {
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
                            if (!context.getGuild().getId().equals("750471488095780966")) {
                                if (canAppeal && isPermenant) {
                                    member.getUser().openPrivateChannel()
                                        .flatMap(u -> u.sendMessage("Please open this message..."))
                                        .flatMap(m -> m.editMessage(member.getAsMention()).setEmbeds(MessageFactory
                                            .makeSuccess(m,
                                                "You have been trello-banned forever within Pinewood, however you are still allowed to appeal within the PBAC.\n\n"
                                                    + "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:)"
                                                    + banLabels.stream().map(c -> "\n - " + c.getName())
                                                    .collect(Collectors.joining()))
                                            .buildEmbed()))
                                        .queue();
                                }

                                if (canAppeal && !isPermenant) {
                                    member.getUser().openPrivateChannel()
                                        .flatMap(u -> u.sendMessage("Please open this message..."))
                                        .flatMap(m -> m.editMessage(member.getAsMention()).setEmbeds(MessageFactory
                                            .makeSuccess(m,
                                                "You have been trello-banned within Pinewood, however you are still allowed to appeal within the PBAC.\n\n"
                                                    + "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:"
                                                    + banLabels.stream().map(c -> "\n - " + c.getName())
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
                                            .setEmbeds(MessageFactory.makeInfo(message, member.getAsMention()
                                                + " tried to verify in `" + context.guild.getName()
                                                + "`. However, this person has a permenant trelloban to his name. He has been sent the STFU video (If his DM's are on) and have been global-banned.")
                                                .buildEmbed()))
                                        .queue();
                                }
                                long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
                                if (mgmLogs != 0) {
                                    TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                                    if (tc != null) {
                                        tc.sendMessageEmbeds(context
                                            .makeInfo("[``:global-unbanned-id`` has tried to verify in "
                                                + context.getGuild().getName()
                                                + " but was trello banned, and has been global-banned. His labels are:](:link):\n"
                                                + "```:reason```")
                                            .set("global-unbanned-id", verificationEntity.getRobloxId())
                                            .set("reason",
                                                banLabels.stream().map(c -> "\n - " + c.getName())
                                                    .collect(Collectors.joining()))
                                            .set("user", "XEUS AUTO BAN")
                                            .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
                                    }
                                }
                                ignoredMembers.put(member, "__*`User is TRELLO BANNED AND GLOBAL BANNED`*__");
                                GlobalBanMember(context, String.valueOf(verificationEntity.getDiscordId()),
                                    "User has been global-banned by PIA. This is automatic ban.", guilde);

                            } else if (canAppeal) {
                                context.guild.modifyMemberRoles(member, context.guild.getRoleById(canAppealRoleId))
                                    .queue();
                                member.getUser().openPrivateChannel()
                                    .flatMap(u -> u.sendMessage("Please open this message..."))
                                    .flatMap(m -> m.editMessage(member.getAsMention())
                                        .setEmbeds(MessageFactory.makeSuccess(m,
                                            "You have been trello-banned within Pinewood, [however you are still allowed to appeal within the PBAC]().\n\n"
                                                + "Your trello-ban has the following labels, I'd suggest sharing these with your ticket handler:"
                                                + banLabels.stream().map(c -> "\n - " + c.getName())
                                                .collect(Collectors.joining()))
                                            .buildEmbed()))
                                    .queue();
                            }

                            if (!canAppeal && isPermenant) {
                                context.guild.modifyMemberRoles(member, context.guild.getRoleById(trellobanRoleId))
                                    .queue();
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
                                        .setEmbeds(MessageFactory.makeInfo(message, member.getAsMention()
                                            + " has a permanent trelloban. They have been sent the STFU video (if their DMs are on).")
                                            .buildEmbed()))
                                    .queue();
                            }

                        } else {
                            ignoredMembers.put(member, "__*`User is TRELLO BANNED`*__");
                            continue;
                        }
                    }
                }
            }

            try {
                Collection accounts = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
                    .where("roblox_user_id", verificationEntity.getRobloxId())
                    .orWhere("roblox_username", verificationEntity.getRobloxUsername()).get();
                if (accounts.size() > 0) {
                    ignoredMembers.put(member, "`User is banned in the MGM Anti-Unban database.`");
                    continue;
                }
            } catch (SQLException throwables) {
                ignoredMembers.put(member, "Database error");
            }

            if (context.getGuild().getId().equals("438134543837560832")) {
                if (avaire.getBlacklistManager().getPBSTBlacklist().contains(verificationEntity.getRobloxId())) {
                    ignoredMembers.put(member, "User is blacklisted from PBST.");
                    continue;
                }
            } else if (context.getGuild().getId().equals("572104809973415943")) {
                if (avaire.getBlacklistManager().getTMSBlacklist().contains(verificationEntity.getRobloxId())) {
                    ignoredMembers.put(member, "User is blacklisted from TMS.");
                    continue;
                }
            } else if (context.getGuild().getId().equalsIgnoreCase("498476405160673286")) {
                if (avaire.getBlacklistManager().getPBMBlacklist().contains(verificationEntity.getRobloxId())) {
                    ignoredMembers.put(member, "User is blacklisted from PBM.");
                    continue;
                }
            } else if (context.getGuild().getId().equalsIgnoreCase("436670173777362944")) {
                if (avaire.getBlacklistManager().getPETBlacklist().contains(verificationEntity.getRobloxId())) {
                    ignoredMembers.put(member, "User is blacklisted from PET.");
                    continue;
                }
            }

            VerificationTransformer verificationTransformer = context.getVerificationTransformer();
            if (verificationTransformer == null) {
                ignoredMembers.put(member, "Verification transformer broke?");
                continue;
            }

            if (verificationTransformer.getRanks() == null || verificationTransformer.getRanks().length() < 2) {
                context.makeError(
                    "Ranks have not been setup on this guild yet. Please ask the admins to setup the roles on this server.")
                    .queue();
                return false;
            }

            List <RobloxUserGroupRankService.Data> robloxRanks = avaire.getRobloxAPIManager().getUserAPI()
                .getUserRanks(verificationEntity.getRobloxId());
            if (robloxRanks == null || robloxRanks.size() == 0) {
                ignoredMembers.put(member, "User doesn't have any group ranks at all.");
                continue;
            }

            GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) avaire.getRobloxAPIManager()
                .toService(verificationTransformer.getRanks(), GuildRobloxRanksService.class);

            Map <GuildRobloxRanksService.GroupRankBinding, Role> bindingRoleMap = guildRanks.getGroupRankBindings()
                .stream()
                .collect(Collectors.toMap(Function.identity(),
                    groupRankBinding -> context.guild.getRoleById(groupRankBinding.getRole()))),
                bindingRoleAddMap = new HashMap <>();

            // Loop through all the group-rank bindings
            bindingRoleMap.forEach((groupRankBinding, role) -> {
                List <String> robloxGroups = robloxRanks.stream()
                    .map(data -> data.getGroup().getId() + ":" + data.getRole().getRank())
                    .collect(Collectors.toList());

                for (String groupRank : robloxGroups) {
                    String[] rank = groupRank.split(":");
                    String groupId = rank[0];
                    String rankId = rank[1];

                    if (groupRankBinding.getGroups().stream().filter(group -> !group.getId().equals("GamePass"))
                        .anyMatch(group -> group.getId().equals(groupId)
                            && group.getRanks().contains(Integer.valueOf(rankId)))) {
                        bindingRoleAddMap.put(groupRankBinding, role);
                    }

                }
            });

            bindingRoleMap.forEach((groupRankBinding, role) -> {
                List <String> gamepassBinds = groupRankBinding.getGroups().stream()
                    .map(data -> data.getId() + ":" + data.getRanks().get(0)).collect(Collectors.toList());

                for (String groupRank : gamepassBinds) {
                    // Loop through all the gamepass-bindings
                    String[] rank = groupRank.split(":");
                    String rankId = rank[1];
                    gamepassBinds.stream().filter(
                        group -> group.split(":")[0].equals("GamePass") && group.split(":")[1].equals(rankId))
                        .forEach(gamepass -> {
                            List <RobloxGamePassService.Datum> rgs = avaire.getRobloxAPIManager().getUserAPI()
                                .getUserGamePass(verificationEntity.getRobloxId(), Long.valueOf(rankId));
                            if (rgs != null) {
                                bindingRoleAddMap.put(groupRankBinding, role);
                            }
                        });

                }
            });

            // Collect the toAdd and toRemove roles from the previous maps
            java.util.Collection <Role> rolesToAdd = bindingRoleAddMap.values().stream()
                .filter(role -> RoleUtil.canBotInteractWithRole(context.getMessage(), role))
                .collect(Collectors.toList()),
                rolesToRemove = bindingRoleMap.values().stream()
                    .filter(role -> !bindingRoleAddMap.containsValue(role)
                        && RoleUtil.canBotInteractWithRole(context.getMessage(), role))
                    .collect(Collectors.toList());

            if (verificationTransformer.getVerifiedRole() != 0) {
                Role r = context.getGuild().getRoleById(verificationTransformer.getVerifiedRole());
                if (r != null) {
                    rolesToAdd.add(r);
                }
            }

            // Modify the roles of the member
            context.getGuild().modifyMemberRoles(member, rolesToAdd, rolesToRemove).queue(l -> count.getAndIncrement(),
                null);

            String rolesToAddAsString = "\nRoles to add:\n" + (rolesToAdd.size() > 0
                ? (rolesToAdd.stream().map(role -> "- `" + role.getName() + "`").collect(Collectors.joining("\n")))
                : "No roles have been added");
            // stringBuilder.append(rolesToAddAsString);

            String rolesToRemoveAsString = "\nRoles to remove:\n"
                + (bindingRoleMap.size() > 0
                ? (rolesToRemove.stream().map(role -> "- `" + role.getName() + "`")
                .collect(Collectors.joining("\n")))
                : "No roles have been removed");
            // stringBuilder.append(rolesToRemoveAsString);

            if (!verificationEntity.getRobloxUsername().equals(member.getEffectiveName())) {
                if (PermissionUtil.canInteract(context.guild.getSelfMember(), member)) {
                    context.getGuild().modifyNickname(member, verificationTransformer.getNicknameFormat()
                        .replace("%USERNAME%", verificationEntity.getRobloxUsername())).queue();
                    // stringBuilder.append("\n\nNickname has been set to
                    // `").append(verificationEntity.getRobloxUsername()).append("`");
                } else {
                    ignoredMembers.put(member,
                        "I do not have the permission to modify their nickname, or their highest rank is above mine.");
                    // stringBuilder.append("\n\nChanging nickname failed :(");
                }
            }

            if (count.get() % 100 == 0) {
                context.makeInfo("`" + count.get() + "`/`" + context.getGuild().getMembers().size() + "`").queue();
            }
        }
        context.getChannel().sendMessage(context.getMember().getAsMention())
            .setEmbeds(context.makeSuccess("All members have been updated").buildEmbed()).queue();

        List <String> failedMembers = new ArrayList <>();
        ignoredMembers.forEach((m, r) -> {
            failedMembers.add("`" + m.getEffectiveName() + "` - **" + r + "**");
        });

        verificationRunning = false;
        builder.setText("All members that failed to update:").setItems(failedMembers);
        builder.build().paginate(context.getChannel(), 0);
        return !verificationRunning;
    }

    private final ArrayList <String> guilds = new ArrayList <String>() {
        {
            add("495673170565791754"); // Aerospace
            add("438134543837560832"); // PBST
            add("791168471093870622"); // Kronos Dev
            add("371062894315569173"); // Official PB Server
            add("514595433176236078"); // PBQA
            add("436670173777362944"); // PET
            add("505828893576527892"); // MMFA
            add("498476405160673286"); // PBM
            add("572104809973415943"); // TMS
            add("758057400635883580"); // PBOP
            add("669672893730258964"); // PB Dev
        }
    };

    private void GlobalBanMember(CommandMessage context, String arg, String reason, List <Guild> guilde) {
        StringBuilder sb = new StringBuilder();
        for (Guild g : guilde) {
            g.ban(arg, 0, "Banned by: " + context.member.getEffectiveName() + "\n" + "For: " + reason
                + "\n*THIS IS A MGM GLOBAL BAN, DO NOT REVOKE THIS BAN WITHOUT CONSULTING THE MGM MODERATOR WHO INITIATED THE GLOBAL BAN, REVOKING THIS BAN WITHOUT MGM APPROVAL WILL RESULT IN DISCIPlINARY ACTION!*")
                .reason("Global Ban, executed by " + context.member.getEffectiveName() + ". For: \n" + reason)
                .queue();

            sb.append("``").append(g.getName()).append("`` - :white_check_mark:\n");
        }
    }

    private boolean isTrelloBanned(VerificationEntity verificationEntity) {
        return avaire.getRobloxAPIManager().getKronosManager().getTrelloBans()
            .containsKey(verificationEntity.getRobloxId());
    }

    private boolean updateAllGuilds(CommandMessage context) {
        if (globalVerificationRunning) {
            context.makeError("Global verification is already running, please wait until it's done.").queue();
            return false;
        }

        if (verificationRunning) {
            context.makeError(
                "Verification is already being done in another guild, please wait until the verification has ended before starting another mass-verification.")
                .queue();
            return false;
        }

        context.makeWarning(
            "Running verification...\nDepending on the user total (:users) this might take a while. You will be mentioned once the time is over.")
            .set("users", context.getJDA().getUsers().size()).queue();
        AtomicInteger count = new AtomicInteger();

        globalVerificationRunning = true;
        HashMap <Long, List <TrellobanLabels>> trellobans = avaire.getRobloxAPIManager().getKronosManager()
            .getTrelloBans();
        for (String guildId : Constants.guilds) {
            HashMap <Member, String> ignoredMembers = new HashMap <>();
            Guild g = avaire.getShardManager().getGuildById(guildId);
            if (g != null) {
                AtomicInteger guildCount = new AtomicInteger();
                for (Member member : g.getMembers()) {
                    if (!bucket.tryConsume(1)) {
                        context.makeInfo("Hit bucket ratelimit, pausing for 30 seconds")
                            .setFooter("This message deletes after 25 seconds.")
                            .queue(m -> m.delete().queueAfter(25, TimeUnit.SECONDS));
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException ignored) {
                        }
                    }

                    if (member == null) {
                        continue;
                    }

                    if (!PermissionUtil.canInteract(g.getSelfMember(), member)) {
                        continue;
                    }

                    if (member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("Xeus Bypass")
                        || r.getName().equalsIgnoreCase("RoVer Bypass"))) {
                        ignoredMembers.put(member, " has the Xeus/RoVer bypass role in `" + g.getName()
                            + "`, this user cannot be verified/updated.");
                        continue;
                    }

                    VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification()
                        .fetchVerificationWithBackup(member.getId(), useCache);
                    if (verificationEntity == null) {
                        continue;
                    }

                    if (trellobans != null) {
                        if (isTrelloBanned(verificationEntity)) {
                            ignoredMembers.put(member, "User is trello-banned. (Not kicked/banned, but not verified)");
                            continue;
                        }
                    }

                    try {
                        Collection accounts = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
                            .where("roblox_user_id", verificationEntity.getRobloxId())
                            .orWhere("roblox_username", verificationEntity.getRobloxUsername()).get();
                        if (accounts.size() > 0) {
                            ignoredMembers.put(member, "User is banned in the MGM Anti-Unban database.");
                            continue;
                        }
                    } catch (SQLException throwables) {
                        ignoredMembers.put(member, "Database error");
                    }

                    if (g.getId().equals("438134543837560832")) {
                        if (avaire.getBlacklistManager().getPBSTBlacklist()
                            .contains(verificationEntity.getRobloxId())) {
                            ignoredMembers.put(member, "User is blacklisted from PBST.");
                            continue;
                        }
                    } else if (g.getId().equals("572104809973415943")) {
                        if (avaire.getBlacklistManager().getTMSBlacklist().contains(verificationEntity.getRobloxId())) {
                            ignoredMembers.put(member, "User is blacklisted from TMS.");
                            continue;
                        }
                    } else if (g.getId().equalsIgnoreCase("498476405160673286")) {
                        if (avaire.getBlacklistManager().getPBMBlacklist().contains(verificationEntity.getRobloxId())) {
                            ignoredMembers.put(member, "User is blacklisted from PBM.");
                            continue;
                        }
                    } else if (context.getGuild().getId().equalsIgnoreCase("436670173777362944")) {
                        if (avaire.getBlacklistManager().getPETBlacklist().contains(verificationEntity.getRobloxId())) {
                            ignoredMembers.put(member, "User is blacklisted from PET.");
                            continue;
                        }
                    }

                    VerificationTransformer verificationTransformer = VerificationController
                        .fetchVerificationFromGuild(avaire, g);
                    if (verificationTransformer == null) {
                        break;
                    }

                    if (verificationTransformer.getRanks() == null || verificationTransformer.getRanks().length() < 2) {
                        context.makeError("Ranks have not been setup on `" + g.getName() + "` yet. Skipped.").queue();
                        break;
                    }

                    List <RobloxUserGroupRankService.Data> robloxRanks = avaire.getRobloxAPIManager().getUserAPI()
                        .getUserRanks(verificationEntity.getRobloxId());
                    if (robloxRanks == null || robloxRanks.size() == 0) {
                        ignoredMembers.put(member, "User doesn't have any group ranks at all.");
                        continue;
                    }

                    GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) avaire.getRobloxAPIManager()
                        .toService(verificationTransformer.getRanks(), GuildRobloxRanksService.class);

                    Map <GuildRobloxRanksService.GroupRankBinding, Role> bindingRoleMap = guildRanks
                        .getGroupRankBindings().stream()
                        .collect(Collectors.toMap(Function.identity(),
                            groupRankBinding -> g.getRoleById(groupRankBinding.getRole()))),
                        bindingRoleAddMap = new HashMap <>();

                    // Loop through all the group-rank bindings
                    bindingRoleMap.forEach((groupRankBinding, role) -> {
                        List <String> robloxGroups = robloxRanks.stream()
                            .map(data -> data.getGroup().getId() + ":" + data.getRole().getRank())
                            .collect(Collectors.toList());

                        for (String groupRank : robloxGroups) {
                            String[] rank = groupRank.split(":");
                            String groupId = rank[0];
                            String rankId = rank[1];

                            if (groupRankBinding.getGroups().stream().filter(group -> !group.getId().equals("GamePass"))
                                .anyMatch(group -> group.getId().equals(groupId)
                                    && group.getRanks().contains(Integer.valueOf(rankId)))) {
                                bindingRoleAddMap.put(groupRankBinding, role);
                            }

                        }
                    });

                    bindingRoleMap.forEach((groupRankBinding, role) -> {
                        List <String> gamepassBinds = groupRankBinding.getGroups().stream()
                            .map(data -> data.getId() + ":" + data.getRanks().get(0)).collect(Collectors.toList());

                        for (String groupRank : gamepassBinds) {
                            // Loop through all the gamepass-bindings
                            String[] rank = groupRank.split(":");
                            String rankId = rank[1];
                            gamepassBinds.stream().filter(group -> group.split(":")[0].equals("GamePass")
                                && group.split(":")[1].equals(rankId)).forEach(gamepass -> {
                                List <RobloxGamePassService.Datum> rgs = avaire.getRobloxAPIManager()
                                    .getUserAPI().getUserGamePass(verificationEntity.getRobloxId(),
                                        Long.valueOf(rankId));
                                if (rgs != null) {
                                    bindingRoleAddMap.put(groupRankBinding, role);
                                }
                            });

                        }
                    });

                    // Collect the toAdd and toRemove roles from the previous maps
                    java.util.Collection <Role> rolesToAdd = bindingRoleAddMap.values().stream()
                        .filter(role -> RoleUtil.canBotInteractWithRole(context.getMessage(), role))
                        .collect(Collectors.toList()),
                        rolesToRemove = bindingRoleMap.values().stream()
                            .filter(role -> !bindingRoleAddMap.containsValue(role)
                                && RoleUtil.canBotInteractWithRole(context.getMessage(), role))
                            .collect(Collectors.toList());

                    if (verificationTransformer.getVerifiedRole() != 0) {
                        Role r = g.getRoleById(verificationTransformer.getVerifiedRole());
                        if (r != null) {
                            rolesToAdd.add(r);
                        }
                    }

                    // Modify the roles of the member
                    g.modifyMemberRoles(member, rolesToAdd, rolesToRemove).queue(l -> {
                        guildCount.getAndIncrement();
                        count.getAndIncrement();
                    }, null);

                    String rolesToAddAsString = "\nRoles to add:\n"
                        + (rolesToAdd.size() > 0
                        ? (rolesToAdd.stream().map(role -> "- `" + role.getName() + "`")
                        .collect(Collectors.joining("\n")))
                        : "No roles have been added");
                    // stringBuilder.append(rolesToAddAsString);

                    String rolesToRemoveAsString = "\nRoles to remove:\n"
                        + (bindingRoleMap.size() > 0
                        ? (rolesToRemove.stream().map(role -> "- `" + role.getName() + "`")
                        .collect(Collectors.joining("\n")))
                        : "No roles have been removed");
                    // stringBuilder.append(rolesToRemoveAsString);

                    if (!verificationEntity.getRobloxUsername().equals(member.getEffectiveName())) {
                        if (PermissionUtil.canInteract(g.getSelfMember(), member)) {
                            g.modifyNickname(member, verificationTransformer.getNicknameFormat().replace("%USERNAME%",
                                verificationEntity.getRobloxUsername())).queue();
                            // stringBuilder.append("\n\nNickname has been set to
                            // `").append(verificationEntity.getRobloxUsername()).append("`");
                        } else {
                            ignoredMembers.put(member,
                                "I do not have the permission to modify their nickname, or their highest rank is above mine.");
                            // stringBuilder.append("\n\nChanging nickname failed :(");
                        }
                    }

                    if (g.getMembers().size() < 100) {
                        continue;
                    }
                    if (guildCount.get() % 100 == 0) {
                        context.makeInfo(
                            "**:guild**: `:currentGuildCount/:guildCount`\n**Global**: `:currentCount/:globalCount`")
                            .set("guild", g.getName()).set("currentGuildCount", guildCount.get())
                            .set("currentCount", count.get()).set("guildCount", g.getMembers().size())
                            .set("globalCount", avaire.getShardManager().getUsers().size()).queue();
                    }
                }
                List <String> failedMembers = new ArrayList <>();
                ignoredMembers.forEach((m, r) -> {
                    failedMembers.add("`" + m.getEffectiveName() + "` - **" + r + "**");
                });

                builder.setText("All members that failed to update within **" + g.getName() + "**:")
                    .setItems(failedMembers);
                builder.build().paginate(context.getChannel(), 0);
            }
        }

        globalVerificationRunning = false;
        context.getChannel().sendMessage(context.getMember().getAsMention())
            .setEmbeds(context.makeSuccess("All members have been updated").buildEmbed()).queue();

        return !globalVerificationRunning;
    }
}
