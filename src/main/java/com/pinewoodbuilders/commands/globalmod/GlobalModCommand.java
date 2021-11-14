package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.CheckPermissionUtil;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.*;

import static com.pinewoodbuilders.utilities.CheckPermissionUtil.GuildPermissionCheckType.GLOBAL_ADMIN;

public class GlobalModCommand extends Command {

    private final static HashSet <String> fuzzyTrue = new HashSet <>(
        Arrays.asList("yes", "y", "on", "enable", "true", "confirm", "1"));
    private final static HashSet <String> fuzzyFalse = new HashSet <>(
        Arrays.asList("no", "n", "off", "disable", "false", "reset", "0"));

    public final HashMap <Guild, Role> role = new HashMap <>();

    public GlobalModCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Global Moderation Command";
    }

    @Override
    public String getDescription() {
        return "Moderate member globally.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Moderate a member globally.");
    }

    @Override
    public List <String> getExampleUsage(@Nullable Message message) {
        return Collections.singletonList("`:command` - Moderate a member globally.");
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("global-mod", "gm");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList("isValidMGMMember");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeError("I'm missing parameters").queue();
            return false;
        }

        if (context.getGuildSettingsTransformer()== null) {
            context.makeError("The guild settings could not be loaded, please contact the developer if this issue persists.").queue();
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "global-ban":
            case "gb":
            case "ban":
                return globalBanCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "global-unban":
            case "gub":
            case "unban":
            case "ub":
                return globalUnbanCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "global-kick":
            case "gk":
            case "kick":
            case "k":
                return globalKickCommand(context, Arrays.copyOfRange(args, 1, args.length));
            default:
                return sendErrorMessage(context, "Please provide an argument to use.");
        }
    }

    private boolean globalKickCommand(CommandMessage context, String[] args) {
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        boolean appealsKick = reason.contains("--appeals-kick");

        try {
            List<Guild> guild = getGuildsByMainGroupId(context.getGuildSettingsTransformer().getMainGroupId());


        if (appealsKick) {
            reason = reason.replace("--appeals-kick", "");
        }

        User u = avaire.getShardManager().getUserById(args[0]);
        if (u != null) {
            String finalReason = reason;
            u.openPrivateChannel().queue(p -> {
                p.sendMessageEmbeds(context.makeInfo(
                    "*You have been **global-kicked** from all the Pinewood Builders discords by an MGM Moderator*. For the reason: *```"
                        + finalReason + "```\n\n"
                        + "You may rejoin the guilds you where kicked from, unless you where banned in one.")
                    .setColor(Color.BLACK).buildEmbed()).queue();
            });
        }
        long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
        if (mgmLogs != 0) {
            TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
            if (tc != null) {
                tc.sendMessageEmbeds(context
                    .makeInfo("[``:global-unbanned-id`` was global-kicked from all discords by :user for](:link):\n"
                        + "```:reason```")
                    .set("global-unbanned-id", args[0]).set("reason", reason)
                    .set("user", context.getMember().getAsMention()).set("link", context.getMessage().getJumpUrl())
                    .buildEmbed()).queue();
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Guild g : guild) {
            if (appealsKick && g.getIdLong() == context.getGuildSettingsTransformer().getGlobalSettings().getAppealsDiscordId()) continue;
            Member m = g.getMemberById(args[0]);
            if (m != null) {
                g.kick(m,
                    "Kicked by: " + context.member.getEffectiveName() + "\n" + "For: " + reason
                        + "\n*THIS IS A MGM GLOBAL KICK*")
                    .reason("Global Kick, executed by " + context.member.getEffectiveName() + ". For: \n" + reason)
                    .queue();
            }
            sb.append("``").append(g.getName()).append("`` - :white_check_mark:\n");
        }
        context.makeSuccess("<@" + args[0] + "> has been kicked from: \n\n" + sb).queue();

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return false;
    }

    private boolean globalBanCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeError("Sorry, but you didn't give any valid argument to use!\n\n" + "``Valid arguments``: \n"
                + " - **sync/s** - Sync all global-bans with a server.\n"
                + " - **v/view/see <discord-id>** - View the reason why someone is global-banned.\n"
                + " - **roblox/rb/roblox-ban <roblox-username/id> <reason>** - Add a roblox account in the auto-ban database `(Appeals Ban not applied with this command)`.\n"
                + " - **discord/d/discord-ban <discord-id> <true/false (Delete messages)> <reason>** - Ban a user on all discords that are connected to the MGI")
                .queue();
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "s":
            case "sync": {
                return syncGlobalPermBansWithGuild(context);
            }
            case "view":
            case "see":
            case "v": {
                return showGlobalBanWithReason(context, args);
            }
            case "mod-bans":
            case "ban-logs":
            case "bl":
            case "mb": {
                return showWhoBannedWho(context, args);
            }
            case "robloxban":
            case "roblox":
            case "rb":
            case "roblox-ban":
                try {
                    return banRobloxIdFromServer(context, args);
                } catch (SQLException throwables) {
                    context.makeError(
                        "Something went wrong when banning the person from their roblox id...\n\n```:error```")
                        .set("error", throwables.getMessage()).queue();
                    throwables.printStackTrace();
                }
            case "d":
            case "discord":
            case "discord-ban":
                return runGlobalBan(context, Arrays.copyOfRange(args, 1, args.length));
            default:
                context.makeInfo(
                    "Please specify if you want to ban someone from their `roblox` account. Or from their `discord` account. (`!global-ban <discord> <true/false> <reason>`/`!global-ban <roblox> <username> <reason>`")
                    .queue();
                return false;
        }
    }

    private boolean globalUnbanCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeError("Sorry, but you didn't give any valid argument to use!\n\n" + "``Valid arguments``: \n"
                //+ " - **roblox/rb/roblox-ban/r <roblox-username/id> <reason>** - Remove a roblox account from the auto-ban database `(Appeals Ban not applied with this command)`.\n"
                + " - **discord/d/discord-ban <discord-id> <true/false (Delete messages)> <reason>** - Unban a user on all discords that are connected to the MGI")
                .queue();
            return false;
        }

        switch (args[0].toLowerCase()) {
            //TODO: Add a way to remove ID's from the global-ban table
            /*case "robloxban":
            case "roblox":
            case "rb":
            case "roblox-ban":
            case "r":
                try {
                    return unbanRobloxId(context, args);
                } catch (SQLException throwables) {
                    context.makeError(
                        "Something went wrong when banning the person from their roblox id...\n\n```:error```")
                        .set("error", throwables.getMessage()).queue();
                    throwables.printStackTrace();
                }*/
            case "d":
            case "discord":
            case "discord-ban":
                return runGlobalUnban(context, Arrays.copyOfRange(args, 1, args.length));
            default:
                context.makeInfo(
                    "Please specify if you want to unban someone from from their `discord/d` account. (`!gm ub <discord/d> `")
                    .queue();
                return false;
        }
    }

    private boolean handleGlobalPermUnban(CommandMessage context, String[] args) throws SQLException {

        String arguments = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean appealsBan = arguments.contains("--appeals-ban") || arguments.contains("-ab");
        boolean globalBan = arguments.contains("--global") || arguments.contains("--g");

        boolean isGBanned = avaire.getGlobalPunishmentManager().isGlobalBanned(context.getGuildSettingsTransformer().getMainGroupId(), args[0]);
        if (isGBanned) {
            List<Guild> guilds = globalBan ? context.getJDA().getGuilds() : getGuildsByMainGroupId(context.getGuildSettingsTransformer().getMainGroupId());
            StringBuilder sb = new StringBuilder();
            for (Guild g : guilds) {
                if (appealsBan || g.getIdLong() == context.getGuildSettingsTransformer().getGlobalSettings().getAppealsDiscordId()) {
                    continue;
                }
                g.retrieveBanById(args[0]).submit().thenAccept(ban -> {
                    g.unban(ban.getUser()).reason("Global unban, executed by: " + context.member.getEffectiveName()).queue();
                    sb.append("``").append(g.getName()).append("`` - :white_check_mark:\n");
                });
            }
            avaire.getGlobalPunishmentManager().unregisterGlobalBan(context.getGuildSettingsTransformer().getMainGroupId(), args[0]);

            TextChannel tc = avaire.getShardManager().getTextChannelById(context.getGuildSettingsTransformer().getGlobalSettings().getMainGroupId());
            if (tc != null) {
                tc.sendMessageEmbeds(context.makeInfo("[``:global-unbanned-id`` was unbanned from all discords by :user](:link)").set("global-unbanned-id", args[0]).set("user", context.getMember().getAsMention()).set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
            }
            context.makeError("**:userId** has been removed from the anti-unban system! + \n\n" + sb).set("userId", args[0]).queue();
        } else {
            context.makeError("This user is not banned!").queue();
        }

        return isGBanned;
    }
    private boolean runGlobalUnban(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeError("Sorry, but you didn't give any member id to globbaly unban!").queue();
            return true;
        }
        if (args.length > 1) {
            context.makeError("Sorry, but you can only globally unban 1 member at a time!").queue();
            return true;
        }

        try {
            return handleGlobalPermUnban(context, args);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return false;
    }

    private boolean runGlobalBan(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeError("You didn't specify what **Discord ID** you want to ban.").queue();
            return true;
        }

        if (args.length == 1) {
            context.makeError("Please supply an true or false argument!").queue();
            return true;
        }

        if (!(fuzzyFalse.contains(args[1]) || fuzzyTrue.contains(args[1]))) {
            context.makeError(
                "Please use either ``true`` or ``false`` as the second argument. (And yes, watch the capitalisation)")
                .queue();
            return false;
        }

        ComparatorUtil.ComparatorType soft = ComparatorUtil.getFuzzyType(args[1]);
        if (args.length < 3) {
            context.makeError("Please supply a reason for the global ban!").queue();
            return true;
        }

        GuildSettingsTransformer settingsTransformer = context.getGuildSettingsTransformer();
        if (settingsTransformer == null) {
            context.makeError("Guildtransformer is null, please contact the developer.").queue();
            return false;
        }

        if (settingsTransformer.getMainGroupId() == 0) {
            context.makeError(
                "MGI (MainGroupId) has not been set. This command is disabled for that. Ask a GA+ to set the MGI.")
                .queue();
            return false;
        }

        boolean isGlobalMod = CheckPermissionUtil.getPermissionLevel(context).getLevel() < GLOBAL_ADMIN.getLevel();
        if (context.getGuildSettingsTransformer().getGlobalSettings() == null && !isGlobalMod) {
            context.makeError(
                "The global settings could not be loaded, please try again later. Otherwise if this issue still persists, contact the developer!")
                .queue();
            return true;
        }

        Guild g = null;
        if (context.getGuildSettingsTransformer().getGlobalSettings().getAppealsDiscordId() != 0) {
            g = avaire.getShardManager().getGuildById(context.getGuildSettingsTransformer().getGlobalSettings().getAppealsDiscordId());
        }

        StringBuilder sb = new StringBuilder();
        try {
            Collection guilds;
            int time = soft.getValue() ? 7 : 0;
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            boolean appealsBan = reason.contains("--appeals-ban") || reason.contains("-ab");
            boolean globalBan = reason.contains("--global") || reason.contains("--g");

            if (context.getGuildSettingsTransformer().getMainGroupId() != 0) {
                guilds = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                    .where("main_group_id", settingsTransformer.getMainGroupId()).get();
            } else {
                if (isGlobalMod) {
                    guilds = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).get();
                } else {
                    context.makeError("Something went wrong...").queue();
                    return false;
                }

            }

            if (guilds.size() == 0) {
                context.makeError("Huh? How did we get here? There should be at least one guild with this ID. Weird...")
                    .queue();
                return false;
            }

            if (guilds.size() == 1) {
                context.makeWarning(
                    "Only 1 guild has the MGI of `:id` meaning the user will only be banned on this discord and stored in the database, if this is correct you can ignore this error.")
                    .queue();
            }


            if (appealsBan) {
                reason = reason.replace("--appeals-ban", "").replace("-ab", "");
            }
            if (globalBan) {
                reason = reason.replace("--global", "").replace("-g", "");
            }

            int bannedGuilds = 0;
            for (DataRow row : guilds) {
                Guild guild = avaire.getShardManager().getGuildById(row.getString("id"));
                if (g != null) {
                    if (g.getId().equals(row.getString("id"))) {
                        if (!appealsBan) {
                            sb.append("``").append(guild.getName()).append("`` - :x:\n");
                            continue;
                        }
                    }
                }
                if (guild == null)
                    continue;
                if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS))
                    continue;
                if (row.getBoolean("global_ban", false)) continue;

                if (row.getBoolean("official_sub_group", false)) {
                        guild.ban(args[0], time, "Banned by: " + context.member.getEffectiveName() + "\n" + "For: "
                            + reason
                            + "\n*THIS IS A MGM GLOBAL BAN, DO NOT REVOKE THIS BAN WITHOUT CONSULTING THE MGM MODERATOR WHO INITIATED THE GLOBAL BAN, REVOKING THIS BAN WITHOUT MGM APPROVAL WILL RESULT IN DISCIPlINARY ACTION!*")
                            .reason("Global Ban, executed by " + context.member.getEffectiveName() + ". For: \n"
                                + reason)
                            .queue();
                    sb.append("``").append(guild.getName()).append("`` - :white_check_mark:\n");
                } else {
                    guild.ban(args[0], time,
                            "This is a global-ban that has been executed from the global ban list of the guild you're subscribed to... ")
                        .queue();
                    sb.append("``").append(guild.getName()).append("`` - :ballot_box_with_check:\n");
                }
                bannedGuilds++;

            }

            User u = avaire.getShardManager().getUserById(args[0]);
            if (u != null) {
                String invite = getFirstInvite(g);
                String finalReason = reason;
                u.openPrivateChannel().submit().thenAccept(p -> p.sendMessageEmbeds(context.makeInfo(
                        "*You have been **global-banned** from all discord that are connected to [this group](:groupLink) by an MGM Moderator. "
                            + "For the reason: *```" + finalReason + "```\n\n"
                            + "If you feel that your ban was unjustified please appeal at the group in question;"
                            + (invite != null ? invite
                            : "Ask an admin of [this group](:groupLink), to create an invite on the appeals discord server of the group."))
                    .setColor(Color.BLACK)
                    .set("groupLink",
                        "https://roblox.com/groups/"
                            + context.getGuildSettingsTransformer().getGlobalSettings().getMainGroupId())
                    .buildEmbed()).submit()).whenComplete((message, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                    }
                });
            }

            long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
            if (mgmLogs != 0) {
                TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
                if (tc != null) {
                    tc.sendMessageEmbeds(context.makeInfo(
                        "[``:global-unbanned-id`` was global-banned from all discords that have global-ban enabled. Banned by ***:user*** in `:guild` for](:link):\n"
                            + "```:reason```")
                        .set("global-unbanned-id", args[0]).set("reason", reason)
                        .set("guild", context.getGuild().getName())
                        .set("user", context.getMember().getAsMention())
                        .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
                }
            }

            context.makeSuccess(
                "<@" + args[0] + "> (" + args[0] + ") has been banned from `:guilds` guilds : \n\n" + sb)
                .set("guilds", bannedGuilds).queue();

            VerificationEntity ve = avaire.getRobloxAPIManager().getVerification()
                .fetchInstantVerificationWithBackup(args[0]);
            try {
                handleGlobalPermBan(context, args, reason, ve);
            } catch (SQLException exception) {
                Xeus.getLogger().error("ERROR: ", exception);
                context.makeError("Something went wrong adding this user to the global perm ban database.").queue();
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return true;
    }

    private String getFirstInvite(Guild g) {
        List <Invite> invites = g.retrieveInvites().complete();
        if (invites == null || invites.size() < 1)
            return null;
        for (Invite i : invites) {
            return i.getUrl();
        }

        return null;
    }

    private boolean banRobloxIdFromServer(CommandMessage context, String[] args) throws SQLException {
        if (args.length < 2) {
            context.makeError("What roblox ID Would you like to ban?").queue();
            return false;
        }

        if (args.length < 3) {
            context.makeError("I'm missing the reason for the ban. Please run the command and add the reason.").queue();
            return false;
        }

        if (NumberUtil.isNumeric(args[1])) {
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            long userId = Long.parseLong(args[1]);
            String username = avaire.getRobloxAPIManager().getUserAPI().getUsername(userId);
            if (username != null) {
                Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
                    .where("roblox_user_id", userId).get();
                /*avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).insert(o -> {
                    o.set("punisherId", context.getAuthor().getId()).set("reason", reason, true)
                        .set("roblox_username", username)
                        .set("main_group_id", context.getGuildSettingsTransformer().getMainGroupId())
                        .set("roblox_user_id", userId);
                });*/

                avaire.getGlobalPunishmentManager().registerGlobalBan(context.getAuthor().getId(),
                    context.getGuildSettingsTransformer().getMainGroupId(),
                    null, userId, username, reason);

                if (c.size() < 1) {
                    context.makeSuccess("Permbanned ``" + args[1] + "`` in the database.").queue();
                } else {
                    context.makeError("This user already has a permban in the database!").queue();
                }
            } else {
                context.makeError("User-ID doesn't exist").queue();
            }
        } else {
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String username = args[1];
            Long userId = avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(username);
            if (userId != null) {
                Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME)
                    .where("roblox_username", args[1]).get();
                if (c.size() < 1) {
                    /*avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).insert(o -> {
                        o.set("punisherId", context.getAuthor().getId()).set("reason", reason, true)
                            .set("roblox_username", username)
                            .set("main_group_id", context.getGuildSettingsTransformer().getMainGroupId())
                            .set("roblox_user_id", userId);
                    });*/

                    avaire.getGlobalPunishmentManager().registerGlobalBan(context.getAuthor().getId(),
                        context.getGuildSettingsTransformer().getMainGroupId(),
                        null, userId, username, reason);



                    context.makeSuccess("Permbanned ``" + args[1] + "`` in the database.").queue();
                    return true;
                } else {
                    context.makeError("This user already has a permban in the database!").queue();
                    return false;
                }
            } else {
                context.makeError("Username doesn't exist").queue();
            }
        }
        return true;
    }

    private boolean showWhoBannedWho(CommandMessage context, String[] args) {
        if (args.length < 2) {
            context.makeError("I don't know what moderator's id you'd like to view.").queue();
            return false;
        }

        String id = args[1];
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("punisherId", id)
                .get();
            if (c.size() < 1) {
                context.makeInfo("This person has not banned anyone.").queue();
                return false;
            }
            StringBuilder sb = new StringBuilder();
            for (DataRow d : c) {
                sb.append("``userId`` (").append(getRobloxUsername(d)).append(
                    ") was banned by <@punisherId> **(``punisherId``)** on all guilds (Connected to `mgi`) for:\n```reason```\n\n"
                        .replace("userId", d.getString("userId"))
                        .replaceAll("punisherId", d.getString("punisherId"))
                        .replace("reason", d.getString("reason")).replace("mgi",
                        String.valueOf(context.getGuildSettingsTransformer().getMainGroupId())));
            }

            return buildAndSendEmbed(sb, context);
        } catch (SQLException throwables) {
            context.makeError("The database coudn't return anything, please check with the developer").queue();
            return false;
        }
    }

    private String getRobloxUsername(DataRow d) {
        String username = avaire.getRobloxAPIManager().getUserAPI().getUsername(d.getLong("roblox_user_id"));
        return username != null ? username : "USERNAME NOT FOUND";
    }

    private boolean showGlobalBanWithReason(CommandMessage context, String[] args) {
        if (args.length < 2) {
            context.makeError("I don't know what user id you'd like to view.").queue();
            return false;
        }

        String id = args[1];
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("userId", id)
                .andWhere("main_group_id", context.getGuildSettingsTransformer().getMainGroupId())
                .get();
            if (c.size() < 1) {
                context.makeInfo("`:userId` was not found/is not banned, please check the bans per connected server for more information.").set("userId", id).requestedBy(context)
                    .queue();
                return true;
            } else if (c.size() == 1) {
                VerificationEntity ve = avaire.getRobloxAPIManager().getVerification()
                    .fetchInstantVerificationWithBackup(id);

                context.makeSuccess("`:userId` (" + getRobloxIdFromVerificationEntity(ve)
                    + ") was banned by <@:punisherId> **(``:punisherId``)** for:\n```:reason```")
                    .set("userId", c.get(0).getString("userId")).set("punisherId", c.get(0).getString("punisherId"))
                    .set("reason", c.get(0).getString("reason")).queue();
                return true;
            } else {
                context.makeError(
                    "Something went wrong, there are more then 1 of the same punishment in the database. Ask Stefano to check this.")
                    .queue();
                return false;
            }
        } catch (SQLException throwables) {
            context.makeError("The database coudn't return anything, please check with the developer").queue();
            return false;
        }
    }

    private Long getRobloxIdFromVerificationEntity(VerificationEntity ve) {
        if (ve != null) {
            return ve.getRobloxId();
        }
        return null;
    }



    private boolean syncGlobalPermBansWithGuild(CommandMessage context) {
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).get();
            context.makeInfo("Syncing **``" + c.size() + "``** global bans to this guild...").queue();

            long mainGroupId = context.getGuildSettingsTransformer().getMainGroupId();
            if (context.getGuildSettingsTransformer().isOfficialSubGroup()) {
                for (DataRow dr : c) {
                    if (dr.getString("userId") == null)
                        continue;
                    if (mainGroupId == 0L) {
                        context.guild.ban(dr.getString("userId"), 0,
                            "THIS BAN MAY ONLY BE REVERTED BY A MGM MODERATOR. ORIGINAL BAN REASON: "
                                + dr.getString("reason"))
                            .reason("Ban sync").queue();
                    } else if (mainGroupId == dr.getLong("main_group_id")) {
                        context.guild.ban(dr.getString("userId"), 0,
                            "THIS BAN MAY ONLY BE REVERTED BY A MGM MODERATOR. ORIGINAL BAN REASON: "
                                + dr.getString("reason"))
                            .reason("Ban sync").queue();
                    } else {
                        continue;
                    }
                }
            } else {
                for (DataRow dr : c) {
                    if (dr.getString("userId") == null)
                        continue;
                    if (mainGroupId == 0L) {
                        context.guild.ban(dr.getString("userId"), 0,
                            "THIS BAN MAY ONLY BE REVERTED BY A MGM MODERATOR. ORIGINAL BAN REASON: "
                                + dr.getString("reason"))
                            .reason("Ban sync").queue();
                    } else if (mainGroupId == dr.getLong("main_group_id")) {
                        context.guild.ban(dr.getString("userId"), 0,
                            "THIS BAN MAY ONLY BE REVERTED BY A MGM MODERATOR. ORIGINAL BAN REASON: "
                                + "Hidden for un-official guilds.")
                            .reason("Ban sync").queue();
                    } else {
                        continue;
                    }
                }

            }
            context.makeSuccess("**``" + c.size() + "``** global bans where synced to this guild...").queue();

        } catch (SQLException exception) {
            Xeus.getLogger().error("ERROR: ", exception);
            context.makeError("Something went wrong when syncing.").queue();
            return false;
        }
        return true;
    }

    private void handleGlobalPermBan(CommandMessage context, String[] args, String reason, VerificationEntity ve)
        throws SQLException {
        Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("userId", args[0])
            .where("main_group_id", context.getGuildSettingsTransformer().getMainGroupId()).get();
        if (c.size() < 1) {
            /*avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).insert(o -> {
                o.set("userId", args[0]).set("punisherId", context.getAuthor().getId()).set("reason", reason, true)
                    .set("roblox_user_id", getRobloxIdFromVerificationEntity(ve))
                    .set("main_group_id", context.getGuildSettingsTransformer().getMainGroupId());
            });*/
            if (ve == null) {
                avaire.getGlobalPunishmentManager().registerGlobalBan(context.getAuthor().getId(),
                    context.getGuildSettingsTransformer().getMainGroupId(),
                    args[0], 0, null, reason);

            } else {
                avaire.getGlobalPunishmentManager().registerGlobalBan(context.getAuthor().getId(),
                    context.getGuildSettingsTransformer().getMainGroupId(),
                    args[0], ve.getRobloxId(), ve.getRobloxUsername(), reason);
            }

            context.makeSuccess("Permbanned ``" + args[0] + "`` in the database.").queue();
        } else {
            context.makeError("This user already has a permban in the database!").queue();
        }
    }

    private boolean buildAndSendEmbed(StringBuilder sb, CommandMessage context) {
        if (sb.length() > 1900) {
            for (String s : splitStringEvery(sb.toString(), 2000)) {
                context.makeWarning(s).queue();
            }
        } else {
            context.makeSuccess(sb.toString()).queue();
        }
        return true;
    }

    public String[] splitStringEvery(String s, int interval) {
        int arrayLength = (int) Math.ceil(((s.length() / (double) interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        } // Add the last bit
        result[lastIndex] = s.substring(j);

        return result;
    }

    public List<Guild> getGuildsByMainGroupId(Long mainGroupId) throws SQLException {
        Collection guildQuery = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("main_group_id", mainGroupId)
            .get();

        List<Guild> guildList = new LinkedList <>();
        for (DataRow dataRow : guildQuery) {
            Guild guild = avaire.getShardManager().getGuildById(dataRow.getString("id"));
            if (guild != null) {
                guildList.add(guild);
            }
        }

        return guildList;
    }

}
