package com.pinewoodbuilders.commands.settings.moderation;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.contracts.commands.settings.SettingsSubCommand;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.User;

import java.sql.SQLException;
import java.util.Arrays;

public class ModSettingsSubCommand extends SettingsSubCommand {

    public ModSettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        super(avaire, command);

    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        int permissionLevel = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (permissionLevel < GuildPermissionCheckType.MAIN_GLOBAL_LEADERSHIP.getLevel()) {
            context.makeError("You are not a MGL (Main Group Leadership). Access is rejected.").queue();
            return true;
        }
        if (args.length < 1) {
            context.makeWarning("""
                    I'm missing the arguments for this command.
                    You noob, get it right.
                     - `add` - Add a moderator to the list of approved mods.
                     - `remove` - Remove a moderator from the list of approved mods
                    """
                    //+ " - `change` - Modify a moderator's permissions."
                    ).queue();
            return false;
        }

        String[] arguments = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase()) {
            case "a":
            case "add":
                return addModeratorToList(context, arguments);
            case "r":
            case "remove":
                return removeModeratorFromList(context, arguments);
            default:
                context.makeError("""
                        I'm missing the arguments for this command.
                        You noob, get it right.
                         - `add` - Add a moderator to the list of approved mods.
                         - `remove` - Remove a moderator from the list of approved mods
                        """
                        //+ " - `change` - Modify a moderator's permissions."
                        ).queue();
                return false;
        }
    }

    private boolean addModeratorToList(CommandMessage context, String[] args) {
        int permissionLevel = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (args.length < 1) {
            context.makeInfo("Please run the command correctly, the prefix is:\n"
                    + " **->** `!settings mod add <discord-id> <roblox-id> (isLeadership (true/false)) (isGlobalMod (true/false))`")
                    .queue();
            return false;
        }

        if (!NumberUtil.isNumeric(args[0])) {
            context.makeError("Incorrect discord ID, please run the command again with a discord id.").queue();
            return false;
        } else {
            User u = avaire.getShardManager().getUserById(args[0]);
            if (u == null && !context.getMessage().getContentRaw().endsWith("--force")) {
                context.makeError(
                        "User is not found in any guild, run the command with `--force` to bypass this restriction")
                        .queue();
                return false;
            }
        }

        VerificationEntity entity = avaire.getRobloxAPIManager().getVerification().fetchVerificationWithBackup(args[0], true);
        if (args.length < 2) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer != null) {
                if (transformer.getMainGroupId() == 0) {
                    context.makeError("A main group ID is not configured on this guild, moderator cannot be added.")
                            .queue();
                    return false;
                }

                if (entity == null) {
                    context.makeError(
                            "There is no record found of this user in the database, or any connected verification registry (Bloxlink, RoVer, etc). Due to security, this user cannot be added to the database, due to it requiring the user ID for security reasons")
                            .queue();
                    return false;
                }

                try {
                    addUserToModerationTable(transformer.getMainGroupId(), entity.getDiscordId(), entity.getRobloxId(),
                            false, false);
                    context.makeSuccess("Added <@" + args[0] + ">").queue();
                    return true;
                } catch (SQLException e) {
                    e.printStackTrace();
                    context.makeError(
                            "Something went wrong when adding the user to the database. Please consult `Stefano#7366`")
                            .queue();
                    return false;
                }
            }
        }

        if (args.length < 3) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer != null) {
                if (transformer.getMainGroupId() == 0) {
                    context.makeError("A main group ID is not configured on this guild, moderator cannot be added.")
                            .queue();
                    return false;
                }

                if (!NumberUtil.isNumeric(args[1])) {
                    context.makeError("Incorrect roblox ID, please run the command again with a discord id.").queue();
                    return false;
                } else {
                    String u = avaire.getRobloxAPIManager().getUserAPI().getUsername(args[1]);
                    if (u == null && !context.getMessage().getContentRaw().endsWith("--force")) {
                        context.makeError(
                                "User is not found on roblox, run the command with `--force` to bypass this restriction")
                                .queue();
                        return false;
                    }
                }

                Long userid = Long.valueOf(args[1]);
                if (entity == null || userid != entity.getRobloxId()) {
                    if (!context.getMessage().getContentRaw().endsWith("--force")) {
                        context.makeError(
                                "You are trying to give someone moderator privileges, however the roblox id stored on their discord account is not the same in the database. (Or they are not in the database, if this is the case. Permissions won't be granted at all) \n\n"
                                        + "Out of security, this action has been cancelled. Run the command with `--force` to bypass this.")
                                .queue();
                        return false;
                    }
                }

                try {
                    addUserToModerationTable(transformer.getMainGroupId(), Long.valueOf(args[0]), userid, false, false);
                    context.makeSuccess("Added <@" + args[0] + ">").queue();
                    return true;
                } catch (SQLException e) {
                    e.printStackTrace();
                    context.makeError(
                            "Something went wrong when adding the user to the database. Please consult `Stefano#7366`")
                            .queue();
                    return false;
                }
            }
        }
        if (permissionLevel < GuildPermissionCheckType.BOT_ADMIN.getLevel()) {
            context.makeError("You are not a BOT Admin. Due to the sensitivity of these specific subcommands. Access is rejected.").queue();
            return true;
        } else {
            if (args.length < 4) {
                GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
                if (transformer != null) {
                    if (transformer.getMainGroupId() == 0) {
                        context.makeError("A main group ID is not configured on this guild, moderator cannot be added.")
                                .queue();
                        return false;
                    }
    
                    if (!NumberUtil.isNumeric(args[1])) {
                        context.makeError("Incorrect roblox ID, please run the command again with a discord id.").queue();
                        return false;
                    } else {
                        String u = avaire.getRobloxAPIManager().getUserAPI().getUsername(args[1]);
                        if (u == null && !context.getMessage().getContentRaw().endsWith("--force")) {
                            context.makeError(
                                    "User is not found on roblox, run the command with `--force` to bypass this restriction")
                                    .queue();
                            return false;
                        }
                    }
    
                    Long userid = Long.valueOf(args[1]);
                    if (entity == null || userid != entity.getRobloxId()) {
                        if (!context.getMessage().getContentRaw().endsWith("--force")) {
                            context.makeError(
                                    "You are trying to give someone moderator privileges, however the roblox id stored on their discord account is not the same in the database. (Or they are not in the database, if this is the case. Permissions won't be granted at all) \n\n"
                                            + "Out of security, this action has been cancelled. Run the command with `--force` to bypass this.")
                                    .queue();
                            return false;
                        }
                    }
    
                    boolean isLead = false;
                    if (args[2].equals("true")) {
                        isLead = true;
                    }
    
                    try {
                        addUserToModerationTable(transformer.getMainGroupId(), Long.valueOf(args[0]), Long.valueOf(args[1]),
                                isLead, false);
                        context.makeSuccess("Added <@" + args[0] + ">").queue();
                        return true;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        context.makeError(
                                "Something went wrong when adding the user to the database. Please consult `Stefano#7366`")
                                .queue();
                        return false;
                    }
                }
            }
            if (args.length < 5) {
                GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
                if (transformer != null) {
                    boolean isGlobal = false;
                    boolean isLead = false;
                    if (args[3].equals("true")) {
                        isGlobal = true;
                    }
                    if (!isGlobal) {
                        if (transformer.getMainGroupId() == 0) {
                            context.makeError("A main group ID is not configured on this guild, moderator cannot be added.")
                                    .queue();
                            return false;
                        }
                        
                        if (args[2].equals("true")) {
                            isLead = true;
                        }
                    }
    
                    if (!NumberUtil.isNumeric(args[1])) {
                        context.makeError("Incorrect roblox ID, please run the command again with a discord id.").queue();
                        return false;
                    } else {
                        String u = avaire.getRobloxAPIManager().getUserAPI().getUsername(args[1]);
                        if (u == null && !context.getMessage().getContentRaw().endsWith("--force")) {
                            context.makeError(
                                    "User is not found on roblox, run the command with `--force` to bypass this restriction")
                                    .queue();
                            return false;
                        }
                    }
    
                    Long userid = Long.valueOf(args[1]);
                    if (entity == null || userid != entity.getRobloxId()) {
                        if (!context.getMessage().getContentRaw().endsWith("--force")) {
                            context.makeError(
                                    "You are trying to give someone moderator privileges, however the roblox id stored on their discord account is not the same in the database. (Or they are not in the database, if this is the case. Permissions won't be granted at all) \n\n"
                                            + "Out of security, this action has been cancelled. Run the command with `--force` to bypass this.")
                                    .queue();
                            return false;
                        }
                    }
    
                    try {
                        addUserToModerationTable(isGlobal ? 0 : transformer.getMainGroupId(), Long.valueOf(args[0]), Long.valueOf(args[1]), isLead, isGlobal);
                        context.makeSuccess("Added <@" + args[0] + ">").queue();
                        return true;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        context.makeError(
                                "Something went wrong when adding the user to the database. Please consult `Stefano#7366`")
                                .queue();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private boolean removeModeratorFromList(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeInfo("Please run the command correctly, the prefix is:\n"
                    + " **->** `!settings mod remove <discord-id>`\n\nDue to ease of use, this will remove **all** cases if the database matches. This is not changable.")
                    .queue();
            return false;
        }
        if (!NumberUtil.isNumeric(args[0])) {
            context.makeError("Incorrect discord ID, please run the command again with a discord id.").queue();
            return false;
        } else {
            User u = avaire.getShardManager().getUserById(args[0]);
            if (u == null && !context.getMessage().getContentRaw().endsWith("--force")) {
                context.makeError(
                        "User is not found in any guild, run the command with `--force` to bypass this restriction")
                        .queue();
                return false;
            }
        }
        Long userId = Long.valueOf(args[0]);
        Collection modsById = getModeratorByDiscordId(userId);
        
        if (modsById.size() > 0) {
            try {
                removeUserFromModerationTable(userId);
                context.makeSuccess("<@"+userId+"> has been removed from the mod database.").queue();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    

    private boolean changeModeratorPermissionFromList(CommandMessage context, String[] args) {
        return false;
    }

}
