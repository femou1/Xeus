package com.pinewoodbuilders.commands.settings.other;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.contracts.commands.settings.SettingsSubCommand;
import com.pinewoodbuilders.utilities.CheckPermissionUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Arrays;
import java.util.List;

public class OtherSettingsSubCommand extends SettingsSubCommand {
    public OtherSettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        super(avaire, command);

    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        int permissionLevel = CheckPermissionUtil.getPermissionLevel(context).getLevel();
        if (permissionLevel < CheckPermissionUtil.GuildPermissionCheckType.MAIN_GLOBAL_LEADERSHIP.getLevel()) {
            context.makeError("You are not a MGL (Main Group Leadership). Access to this command is rejected.").queue();
            return true;
        }
        if (args.length < 1) {
            context.makeWarning("I'm missing the arguments for this command.\nYou noob, get it right.\n"
                    + " - `bypass` - Add a moderator to the list of approved mods.\n"
                    + " - `permission-override` - Remove a moderator from the list of approved mods\n"
            // + " - `change` - Modify a moderator's permissions."
            ).queue();
            return false;
        }

        String[] arguments = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase()) {
            case "bypass":
                return setBypassOption(context, arguments, permissionLevel);
            case "permission-override":
            case "po":
            case "permov":
                return togglePermissionOverride(context);
            default:
                context.makeError("I'm missing the arguments for this command.\nYou noob, get it right.\n"
                        + " - `permission-override`").queue();
                return false;
        }
    }

    private boolean setBypassOption(CommandMessage context, String[] arguments, int permissionLevel) {
        if (!context.guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
            return noPermissionError(context);
        }
        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.makeError("Sorry, but you are not allowed to set a bypass, this command requires the \"`Administrator`\" permission on the discord you are running this on. Due to the sensitivty of this command.").queue();
            return false;
        }

        context.makeSuccess("Command bypass has been enabled, MGM (Main Global Leadership) or higher can now bypass certain permissions and is now allowed to use the permission override feature. (See ``");
        return false;
    }

    private boolean togglePermissionOverride(CommandMessage context) {
        if (!context.guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
            return noPermissionError(context);
        }
        List<Role> r = context.getGuild().getRolesByName("XEUSBYPASS", true);
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
