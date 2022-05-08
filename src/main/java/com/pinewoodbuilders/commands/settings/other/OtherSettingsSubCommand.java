package com.pinewoodbuilders.commands.settings.other;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.contracts.commands.settings.SettingsSubCommand;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class OtherSettingsSubCommand extends SettingsSubCommand {
    public OtherSettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        super(avaire, command);

    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        int permissionLevel = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (permissionLevel < GuildPermissionCheckType.MAIN_GLOBAL_LEADERSHIP.getLevel()) {
            context.makeError("You are not a MGL (Main Group Leadership). Access to this command is rejected.").queue();
            return true;
        }
        if (args.length < 1) {
            context.makeWarning("""
                    I'm missing the arguments for this command.
                    You noob, get it right.
                     - `bypass` - Add a moderator to the list of approved mods.
                     - `permission-override` - Remove a moderator from the list of approved mods
                    """
                // + " - `change` - Modify a moderator's permissions."
            ).queue();
            return false;
        }

        String[] arguments = Arrays.copyOfRange(args, 1, args.length);
        return switch (args[0].toLowerCase()) {
            case "bypass" -> setBypassOption(context, arguments, permissionLevel);
            case "permission-override", "po", "permov" -> togglePermissionOverride(context);
            case "leadership-server" -> leadershipServerChange(context, arguments);
            default -> command.sendErrorMessage(context, """
                I'm missing the arguments for this command.
                You noob, get it right.
                 - `permission-override`
                 - `bypass`
                 - `leadership-server` - Will toggle the leadership features of a guild.""");
        };
    }

    private boolean leadershipServerChange(CommandMessage context, String[] arguments) {
        GuildSettingsTransformer settings = context.getGuildSettingsTransformer();
        if (settings == null) return command.sendErrorMessage(context, "Global settings have not been set.");

        settings.setLeadershipServer(!settings.isLeadershipServer());
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.guild.getId())
                .update(l -> {
                    l.set("leadership_server", true);
                });
            context.makeSuccess((settings.isLeadershipServer() ? "Set" : "Unset") + " this server as a leadership server.").queue();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    private boolean setBypassOption(CommandMessage context, String[] arguments, int permissionLevel) {
        if (!context.guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
            return noPermissionError(context);
        }
        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.makeError("Sorry, but you are not allowed to set a bypass, this command requires the \"`Administrator`\" permission on the discord you are running this on. Due to the sensitivty of this command.").queue();
            return false;
        }

        GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, context.getGuild());
        if (settings == null)
            return command.sendErrorMessage(context, "Settings Transformer could not be pulled, nothing has changed");

        settings.setPermissionBypass(!settings.getPermissionBypass());
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).where("id", context.getGuild().getId())
                .update(l -> {
                    l.set("permission_bypass", true);
                });
            context.makeSuccess((settings.getPermissionBypass() ? "Enabled" : "Disabled") + " the new moderation system in " + context.guild.getName()).queue();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        context.makeSuccess("Command bypass has been enabled, MGM (Main Global Leadership) or higher can now bypass certain permissions and is now allowed to use the permission override feature.`");
        return false;
    }

    private boolean newModerationSystem(CommandMessage context, GuildSettingsTransformer guildTransformer) {
        GlobalSettingsTransformer settings = guildTransformer.getGlobalSettings();
        if (settings == null) return command.sendErrorMessage(context, "Global settings have not been set.");


        return false;
    }

    private boolean togglePermissionOverride(CommandMessage context) {
        if (!context.guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
            return noPermissionError(context);
        }
        List <Role> r = context.getGuild().getRolesByName("XEUS-BYPASS", true);
        if (r.size() < 1) {
            context.guild.createRole().setHoisted(false).setMentionable(false).setName("XEUS-BYPASS")
                .setPermissions(Permission.ADMINISTRATOR).queue(role -> {
                    runRoleGiveAction(context, role);
                });
            return true;
        }
        runRoleGiveAction(context, r.get(0));
        return true;
    }

    private boolean noPermissionError(CommandMessage context) {
        context.makeError("The bot does not have `Administrator` privileges on this discord. Due to security, this command will not work and it has been disabled. Only if the bot has administrator, this command will work.").queue();
        return false;
    }

    private void runRoleGiveAction(CommandMessage context, Role role) {
        Member m = context.getMember();
        if (m.getRoles().contains(role)) {
            context.guild.removeRoleFromMember(m, role);
            context.makeSuccess(
                    "Your role was removed from your account. This has been logged in the server Audit Logs and the developer of the bot.")
                .queue();

        } else {
            context.guild.addRoleToMember(m, role);
            context.makeSuccess(
                    "Your role was added to your account. This has been logged in the server Audit Logs and the developer of the bot.")
                .queue();
        }
    }
}
