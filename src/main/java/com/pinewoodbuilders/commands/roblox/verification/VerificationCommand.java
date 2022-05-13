package com.pinewoodbuilders.commands.roblox.verification;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.VerificationCommandContract;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.controllers.VerificationController;
import com.pinewoodbuilders.database.transformers.VerificationTransformer;
import com.pinewoodbuilders.requests.service.group.GroupRanksService;
import com.pinewoodbuilders.requests.service.group.GuildRobloxRanksService;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.RoleUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import com.pinewoodbuilders.utilities.menu.Paginator;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VerificationCommand extends VerificationCommandContract {
    private final Paginator.Builder builder;


    public VerificationCommand(Xeus avaire) {
        super(avaire, false);
        builder = new Paginator.Builder()
            .setColumns(1)
            .setFinalAction(m -> {try {m.clearReactions().queue();} catch (PermissionException ignore) {}})
            .setItemsPerPage(1)
            .waitOnSinglePage(false)
            .showPageNumbers(true)
            .wrapPageEnds(true)
            .setEventWaiter(avaire.getWaiter())
            .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public String getName() {
        return "Verification Command";
    }

    @Override
    public String getDescription() {
        return "Control multiple settings for the bots verification counterparts.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command` - Bind a role to a rank."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command`"
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isGuildLeadership"
        );
    }

    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("verification");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (context.getGuildTransformer() == null) {
            context.makeError("Can't get the GuildTransformer, please try again later.").queue();
            return false;
        }

        if (context.getVerificationTransformer() == null) {
            context.makeError("Can't get the VerificationTransformer, please try again later.").queue();
            return false;
        }


        RobloxAPIManager manager = avaire.getRobloxAPIManager();
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "sac":
                case "set-announce-channel":
                    return setAnnouncementChannel(context, args);
                case "vc":
                case "verifychannel":
                    return setVerifyChannel(context, args);
                case "verified-role":
                case "vr":
                    return setVerifiedRole(context, args);
                case "b":
                case "bind":
                    return runBindCommand(context, args);
                case "createverifychannel":
                    return createVerifyChannel(context, args);
                case "joindm":
                    return setJoinDM(context, args);
                case "nickname":
                    return setNicknameUsers(context, args);
                case "unbindall":
                    return unbindAllSubcommand(context, args);
                case "unbind":
                    return unbindCommand(context, args);
                case "list":
                    return listBoundRoles(context, manager);
                case "creategroupranks":
                    return createGroupRanks(context, args, manager);
                case "mass-unbind":
                    return massUnbindUsers(context, manager);
                case "get-user-id":
                    return getUserIds(context, manager);
                case "kick-unranked":
                    return kickUnranked(context, manager);
                default:
                    context.makeError("Invalid argument given.").queue();
                    return false;
            }
        }
        return false;
    }

    private boolean kickUnranked(CommandMessage context, RobloxAPIManager manager) {
        if (XeusPermissionUtil.getPermissionLevel(context).getLevel() < GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
            context.makeError("You're required to be an server admin or above to run this command").queue();
            return false;
        }

        if (context.getGuildSettingsTransformer().getMainDiscordRole() == 0) {
            context.makeError("Main discord role has not been set, please add the main discord role (;rmanage set-main-role").queue();
            return false;
        }

        List <Member> members = new ArrayList <>();
        int count = 0;
        for (Member m : context.guild.getMembers()) {
            if (!PermissionUtil.canInteract(context.getGuild().getSelfMember(), m)) {
                continue;
            }

            if (m.getRoles().stream().anyMatch(r -> r.getIdLong() == context.getGuildSettingsTransformer().getMainDiscordRole())) {
                continue;
            }

            if (m.getRoles().stream().anyMatch(r -> r.isManaged() || r.getName().equalsIgnoreCase("RoVer Bypass") || r.getName().equalsIgnoreCase("Xeus Bypass"))) {
                continue;
            }
            count++;
            members.add(m);
            //   System.out.println(m.getEffectiveName() + " ("+m.getUser().getName() + "#" + m.getUser().getDiscriminator() +")");
        }
        int finalCount = count;
        context.makeWarning("Would you like to prune `:count` member who don't have the main discord role.").set("count", count).queue(countMessage -> {
            countMessage.addReaction("\uD83D\uDC4D").queue();
            countMessage.addReaction("\uD83D\uDC4E").queue();

            builder.setItems(members.stream().map(member -> "\n- `"+member.getEffectiveName() + "`").collect(Collectors.toList()));
            builder.setItemsPerPage(10).build().paginate(context.getChannel(), 0);

            avaire.getWaiter().waitForEvent(MessageReactionAddEvent.class, check -> check.getMember().equals(context.member) && check.getMessageId().equals(countMessage.getId()), action -> {

                    switch (action.getReactionEmote().getName()) {
                        case "\uD83D\uDC4D":
                            for (Member m : members) {
                                m.getUser().openPrivateChannel().queue(k -> k.sendMessage("You have been kicked from `" + context.getGuild().getName() + "` due to not being verified in the group. If you want to join back, rejoin by going to the group page and clicking the \"Discord\" button. \n" +
                                    "\n").queue());
                                action.getGuild().kick(m, "Unverified kick - Not in the group.").queue();
                            }
                            context.makeSuccess("`:count` members have been kicked!").set("count", finalCount).queue();
                            break;
                        case "\uD83D\uDC4E":
                            context.makeInfo("Stopped kick, no-one has been kicked.").queue();
                    }
                }
            );
        });

        return true;
    }

    private boolean getUserIds(CommandMessage context, RobloxAPIManager manager) {
        if (XeusPermissionUtil.getPermissionLevel(context).getLevel() < GuildPermissionCheckType.BOT_ADMIN.getLevel()) {
            context.makeError("You're required to be an facilitator, bot admin or above to run this command").queue();
            return false;
        }

        StringBuilder sb = new StringBuilder();
        for (User u : context.getJDA().getUsers()) {
            VerificationEntity ve = manager.getVerification().callUserFromDatabaseAPI(u.getId());
            if (ve != null) continue;
            sb.append(u.getId()).append(",");
        }
        System.out.println(sb);
        context.makeSuccess("Please check the console!").queue();
        return true;
    }

    private boolean massUnbindUsers(CommandMessage context, RobloxAPIManager manager) {
        if (XeusPermissionUtil.getPermissionLevel(context).getLevel() < GuildPermissionCheckType.BOT_ADMIN.getLevel()) {
            context.makeError("You're required to be an facilitator, bot admin or above to run this command").queue();
            return false;
        }
        GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) manager.toService(context.getVerificationTransformer().getRanks(), GuildRobloxRanksService.class);

        Map <GuildRobloxRanksService.GroupRankBinding, Role> bindingRoleMap =
            guildRanks.getGroupRankBindings().stream()
                .collect(Collectors.toMap(Function.identity(), groupRankBinding -> context.getGuild().getRoleById(groupRankBinding.getRole())));
        List <Role> rolesToRemove = bindingRoleMap.values()
            .stream().filter(role -> RoleUtil.canBotInteractWithRole(context.getMessage(), role)).collect(Collectors.toList());

        for (Member m : context.getGuild().getMembers()) {
            if (!PermissionUtil.canInteract(context.getGuild().getSelfMember(), m)) continue;
            VerificationEntity ve = manager.getVerification().callUserFromDatabaseAPI(m.getId());
            if (ve != null) continue;
            context.guild.modifyMemberRoles(m, null, rolesToRemove).queue();
        }

        context.makeSuccess("All members that aren't in the database had their roles stripped!").queue();
        return true;
    }

    private boolean setVerifiedRole(CommandMessage context, String[] args) {
        if (args.length == 1) {
            context.makeError("Please run this argument again with the ID of the text channel you want to use (Or mention the channel).").queue();
            return false;
        }

        if (args.length > 2) {
            context.makeError("Please only give me the channel id you'd like to use.").queue();
            return false;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            return changeSettingTo(context, null, "verified_role");
        }

        Role gc = MentionableUtil.getRole(context.getMessage(), args);
        if (gc != null) {
            return changeSettingTo(context, gc.getIdLong(), "verified_role");
        }
        context.makeError("Unable to update channel id.").queue();

        return false;
    }

    private boolean listBoundRoles(CommandMessage context, RobloxAPIManager manager) {
        GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) manager.toService(context.getVerificationTransformer().getRanks(), GuildRobloxRanksService.class);
        if (guildRanks != null) {
            List <String> finalMessage = new LinkedList <>();
            guildRanks.getGroupRankBindings().forEach(p -> {
                StringBuilder sb = new StringBuilder();
                Role r = context.getGuild().getRoleById(p.getRole());
                if (r != null) {
                    sb.append("**").append(r.getName()).append("**\n");
                } else {
                    sb.append("**").append("INVALID ROLE (`").append(p.getRole()).append("`)").append("** ");
                }

                p.getGroups().forEach(group -> {
                    sb.append("``").append(group.getId()).append("``").append("\n");
                    if (group.getRanks().size() == 255) {
                        sb.append("```").append(group.getRanks().get(0)).append("-")
                            .append(group.getRanks().get(group.getRanks().size() - 1)).append("```\n\n");
                    } else {
                        sb.append("```").append(group.getRanks()).append("```\n\n");
                    }
                });


                finalMessage.add(sb.toString());
            });
            builder.setText("Current questions in the list: ")
                .setItems(finalMessage)
                .setLeftRightText("Click this to go left", "Click this to go right")
                .setUsers(context.getAuthor())
                .setColor(context.getGuild().getSelfMember().getColor());

            builder.build().paginate(context.getChannel(), 0);
            return true;
        } else {
            context.makeError("Groups have not been setup yet. Please set them up using `:verification creategroupranks (group id)`").queue();
            return false;
        }
    }

    private boolean createGroupRanks(CommandMessage context, String[] args, RobloxAPIManager manager) {
        if (args.length > 1) {
            if (args.length == 2 && NumberUtil.isNumeric(args[1])) {
                GroupRanksService ranks = manager.getGroupAPI().fetchGroupRanks(Integer.valueOf(args[1]), false);
                context.makeInfo("Ranks have not been setup yet, loading ranks from Roblox API and binding them to the guild based on the roblox group `:gId`.").set("gId", args[1]).queue();
                return generateRobloxRanks(context, ranks);
            }
        }
        if (context.getGuildSettingsTransformer().getRobloxGroupId() != 0) {
            GroupRanksService ranks = manager.getGroupAPI().fetchGroupRanks(context.getGuildSettingsTransformer().getRobloxGroupId(), false);
            context.makeInfo("Ranks have not been setup yet, loading ranks from Roblox API and binding them to the guild based on the main group ID.").queue();
            return generateRobloxRanks(context, ranks);
        }
        return sendErrorMessage(context, "Guild doesn't have a main group ID configured, neither did you supply one. Please try again.");
    }

    private boolean setJoinDM(CommandMessage context, String[] args) {
        /*Future <Integer> submit = ScheduleHandler.getScheduler().submit(() -> {
            //avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername("Roblox");
            return 1;
        });
        while (!submit.isDone()) {

        }

        try {
            Integer result = submit.get();
            context.makeInfo(result + " - Dave").queue();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }*/
        return false;
    }

    private boolean unbindAllSubcommand(CommandMessage context, String[] args) {
        return false;
    }

    private boolean createVerifyChannel(CommandMessage context, String[] args) {
        createVerificationCategory(context.guild).queue();
        return false;
    }

    private boolean unbindCommand(CommandMessage context, String[] args) {
        VerificationTransformer verificationTransformer = context.getVerificationTransformer();
        if (verificationTransformer == null) {
            context.makeError("The VerificationTransformer seems to not have been filled, please try again in 5 minutes.").queue();
            return false;
        }

        GuildRobloxRanksService binds = (GuildRobloxRanksService) avaire.getRobloxAPIManager().toService(context.getVerificationTransformer().getRanks(), GuildRobloxRanksService.class);
        if (!NumberUtil.isNumeric(args[1])) {
            context.makeError("The role has to be an ID, the name won't work.").queue();
            return false;
        }

        if (binds == null) {
            context.makeError("No bound roles have been found :(").queue();
            return false;
        }

        if (binds.getGroupRankBindings().stream().noneMatch(m -> m.getRole().equals(args[1]))) {
            context.makeError("Can't find a role with this ID.").queue();
            return false;
        }

        binds.getGroupRankBindings().stream().filter(m -> m.getRole().equals(args[1])).toList().forEach(
            l -> {
                binds.getGroupRankBindings().remove(l);
            }
        );

        binds.setGroupRankBindings(binds.getGroupRankBindings());
        try {
            avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_SETTINGS_TABLE_NAME)
                .where("id", context.getGuild().getId()).update(r -> r.set("ranks", Xeus.gson.toJson(binds)));
            VerificationController.forgetCache(context.getGuild().getIdLong());
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            context.makeError("Something went wrong *pouts*").queue();
            return false;
        }
        context.makeSuccess("Removed `:roleId` from binds.").set("roleId", args[1]).queue();
        return true;
    }

    private boolean runBindCommand(CommandMessage context, String[] args) {
        VerificationTransformer verificationTransformer = context.getVerificationTransformer();
        if (verificationTransformer == null) {
            context.makeError("The VerificationTransformer seems to not have been filled, please try again in 5 minutes.").queue();
            return false;
        }

        if (verificationTransformer.getNicknameFormat() == null) {
            context.makeError("The nickname format is not set (Wierd, it's the default but ok then, command cancelled?).").queue();
            return false;
        }

        //if (args.length < 3) return goToBindStartWaiter(context, args);

        GuildRobloxRanksService binds = (GuildRobloxRanksService) avaire.getRobloxAPIManager().toService(context.getVerificationTransformer().getRanks(), GuildRobloxRanksService.class);
        if (!NumberUtil.isNumeric(args[1])) {
            context.makeError("The role has to be an ID, the name won't work.").queue();
            return false;
        }

        Role role = context.getGuild().getRoleById(args[1]);
        if (role == null) {
            context.makeError("The role doesn't exist. Please check if you've copied the correct ID.").queue();
            return false;
        }

        if (binds != null) {
            if (binds.getGroupRankBindings().stream().anyMatch(m -> m.getRole().equals(args[1]))) {
                context.makeError("This role is already bound to a group/groups, unbind this role to bind a group/groups again.").queue();
                return false;
            }
        }

        if (!RoleUtil.canBotInteractWithRole(context.message, role)) return false;
        if (!(args[2].contains(":") || NumberUtil.isNumeric(args[2]))) {
            context.makeError("Make sure the argument for the group ID is a number!").queue();
            return false;
        }

        final String groupsAndRanks = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final String[] groupAndRanksList = groupsAndRanks.split(" ");

        List <GuildRobloxRanksService.GroupRankBinding> groupRankBindings = new ArrayList <>();
        List <GuildRobloxRanksService.Group> groups = new LinkedList <>();
        for (String groupAndRank : groupAndRanksList) {
            List <Integer> ranks = new LinkedList <>();
            if (groupAndRank.contains(":")) {
                String[] groupAndRankSeperated = groupAndRank.split(":");
                if (groupAndRankSeperated[1].contains("-")) {
                    String[] ranksBetween = groupAndRankSeperated[1].split("-");
                    int highNum, lowNum;
                    lowNum = Integer.parseInt(ranksBetween[0]);
                    highNum = Integer.parseInt(ranksBetween[1]);

                    if (!(lowNum > 0 && highNum < 256)) {
                        context.makeError("Incorrect number found in ranks, make sure either ranks are **lower** then 256, or **higher** then 0. Skipped group :group.")
                            .set("group", groupAndRankSeperated[0]).queue();
                        continue;
                    }

                    for (int i = lowNum; i <= highNum; i++) {
                        lowNum++;
                        ranks.add(i);
                    }
                } else {
                    if (groupAndRankSeperated[1].contains(",")) {
                        String[] ranksFromSingleGroup = groupAndRankSeperated[1].split(",");
                        for (String rank : ranksFromSingleGroup) {
                            ranks.add(Integer.parseInt(rank));
                        }
                    } else {
                        ranks.add(Integer.parseInt(groupAndRankSeperated[1]));
                    }

                }
            } else {
                int lowNumber = 1;
                for (int i = lowNumber; i <= 255; i++) {
                    lowNumber++;
                    ranks.add(i);
                }
            }

            String groupId = groupAndRank;
            if (groupId.contains(":")) {
                groupId = groupId.split(":")[0];
            }
            groups.add(new GuildRobloxRanksService.Group(groupId, ranks));
        }
        groupRankBindings.add(new GuildRobloxRanksService.GroupRankBinding(args[1], groups));

        if (binds != null) groupRankBindings.addAll(binds.getGroupRankBindings());
        List <String> finalMessage = new LinkedList <>();
        groupRankBindings.forEach(p -> {
            StringBuilder sb = new StringBuilder();
            Role r = context.getGuild().getRoleById(p.getRole());
            if (r != null) {
                sb.append("**").append(r.getName()).append("**\n");
            } else {
                sb.append("**").append("INVALID ROLE (`").append(p.getRole()).append("`)").append("** ");
            }

            p.getGroups().forEach(group -> {
                sb.append("``").append(group.getId()).append("``").append("\n");
                if (group.getRanks().size() == 255) {
                    sb.append("```").append(group.getRanks().get(0)).append("-")
                        .append(group.getRanks().get(group.getRanks().size() - 1)).append("```\n\n");
                } else {
                    sb.append("```").append(group.getRanks()).append("```\n\n");
                }
            });

            finalMessage.add(sb.toString());
        });
        builder.setText("Current questions in the list: ")
            .setItems(finalMessage)
            .setLeftRightText("Click this to go left", "Click this to go right")
            .setUsers(context.getAuthor())
            .setColor(context.getGuild().getSelfMember().getColor());

        builder.build().paginate(context.getChannel(), 0);

        binds.setGroupRankBindings(groupRankBindings);
        try {
            avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_SETTINGS_TABLE_NAME)
                .where("id", context.getGuild().getId()).update(r -> r.set("ranks", Xeus.gson.toJson(binds)));
            VerificationController.forgetCache(context.getGuild().getIdLong());
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            context.makeError("Something went wrong *pout*").queue();
        }
        return false;
    }

    private boolean setNicknameUsers(CommandMessage context, String[] args) {
        return false;
    }


    public RestAction <Message> createVerificationCategory(Guild guild) {
        return guild.createCategory("Verification")
            .addPermissionOverride(guild.getSelfMember(), EnumSet.of(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL), null)
            .flatMap((category) -> category.createTextChannel("verify"))
            .flatMap((channel) -> channel.sendMessage("Hello! In this channel, all verification commands are being posted. All messages (eventually) get deleted!"))
            .flatMap((channel) -> channel.getCategory().createTextChannel("verify-instructions")
                .addPermissionOverride(guild.getPublicRole(), EnumSet.of(Permission.VIEW_CHANNEL), EnumSet.of(Permission.MESSAGE_SEND)))
            .flatMap((channel) -> channel.sendMessageEmbeds(new EmbedBuilder().setDescription("""
                This server uses a Roblox verification system. In order to unlock all the features of this server, you'll need to verify your Roblox account with your Discord account!
                Make sure you have a Roblox account that's in any of the Pinewood Builders groups!
                If you don't have one, you can create one here: https://www.roblox.com/my/account/create
                
                To do so, simply click the link below and follow the instructions.
                Once you've done that, you'll be able to speak in the server.
                """).setColor(Color.GREEN).build()));
    }


    private boolean setAnnouncementChannel(CommandMessage context, String[] args) {
        if (args.length == 1) {
            context.makeError("Please run this argument again with the ID of the text channel you want to use (Or mention the channel).").queue();
            return false;
        }

        if (args.length > 2) {
            context.makeError("Please only give me the channel id you'd like to use.").queue();
            return false;
        }

        GuildChannel gc = MentionableUtil.getChannel(context.getMessage(), args);
        if (gc != null) {
            if (gc.getType().equals(ChannelType.TEXT)) {
                return changeSettingTo(context, gc.getIdLong(), "announce_channel");
            }
        }
        context.makeError("Unable to update channel id.").queue();
        return false;
    }

    private boolean setVerifyChannel(CommandMessage context, String[] args) {
        if (args.length == 1) {
            context.makeError("Please run this argument again with the ID of the text channel you want to use (Or mention the channel).").queue();
            return false;
        }

        if (args.length > 2) {
            context.makeError("Please only give me the channel id you'd like to use.").queue();
            return false;
        }

        GuildChannel gc = MentionableUtil.getChannel(context.getMessage(), args);
        if (gc != null) {
            if (gc.getType().equals(ChannelType.TEXT)) {
                return changeSettingTo(context, gc.getIdLong(), "verify_channel");
            }
        }
        context.makeError("Unable to update channel id.").queue();
        return false;
    }

    private boolean changeSettingTo(CommandMessage context, Object tc, String setting) {
        try {
            avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_SETTINGS_TABLE_NAME).where("id", context.getGuild().getId())
                .update(m -> {
                    m.set(setting, tc);
                    context.makeSuccess("Updated `:setting` to `:value` in the VerificationTransformer")
                        .set("value", tc).set("setting", setting).queue();
                });
            return true;
        } catch (SQLException throwables) {
            context.makeError("Something went wrong trying to update `:setting` to `:value`.")
                .set("value", tc).set("setting", setting)
                .queue();
            return false;
        }
    }

    private boolean generateRobloxRanks(CommandMessage context, GroupRanksService ranks) {
        context.makeInfo("```json\n" + buildPayload(context, ranks) + "\n```").queue();
        try {
            avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_SETTINGS_TABLE_NAME).where("id", context.getGuild().getId()).update(m -> {
                m.set("ranks", buildPayload(context, ranks), true);
            });
            VerificationController.forgetCache(context.getGuild().getIdLong());
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return false;
    }

    private JSONObject buildPayload(CommandMessage context, GroupRanksService ranks) {
        JSONObject groupRankBindings = new JSONObject();
        JSONArray groupRanksBindings = new JSONArray();
        for (GroupRanksService.Role role : ranks.getRoles()) {
            Guild g = context.getGuild();
            List <Role> r = g.getRolesByName(role.getName(), true);
            if (r.size() > 0) {
                if (RoleUtil.canBotInteractWithRole(context.getMessage(), r.get(0))) {
                    JSONObject roleObject = new JSONObject();
                    roleObject.put("role", r.get(0).getId());

                    JSONArray groups = new JSONArray();
                    JSONObject group = new JSONObject();
                    group.put("id", ranks.getGroupId());
                    List <Integer> Rranks = new ArrayList <>();
                    Rranks.add(role.getRank());
                    group.put("ranks", Rranks);
                    groups.put(group);

                    roleObject.put("groups", groups);

                    groupRanksBindings.put(roleObject);
                }
            } else {
                Role m = context.getGuild().createRole().setName(role.getName()).complete();
                JSONObject roleObject = new JSONObject();
                roleObject.put("role", m.getId());

                JSONArray groups = new JSONArray();
                JSONObject group = new JSONObject();
                group.put("id", ranks.getGroupId());
                List <Integer> Rranks = new ArrayList <>();
                Rranks.add(role.getRank());
                group.put("ranks", Rranks);
                groups.put(group);

                roleObject.put("groups", groups);

                groupRanksBindings.put(roleObject);
            }
        }

        groupRankBindings.put("groupRankBindings", groupRanksBindings);
        context.makeInfo("```json\n" + groupRanksBindings + "\n```").queue();
        return groupRankBindings;
    }
}
