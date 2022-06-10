package com.pinewoodbuilders.commands.settings;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.global.GlobalSettingsSubCommand;
import com.pinewoodbuilders.commands.settings.moderation.ModSettingsSubCommand;
import com.pinewoodbuilders.commands.settings.other.OtherSettingsSubCommand;
import com.pinewoodbuilders.commands.settings.server.ServerSettingsSubCommand;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.Member;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GuildAndGlobalSettingsCommand extends Command {

    private final ServerSettingsSubCommand server;
    private final ModSettingsSubCommand mod;
    private final GlobalSettingsSubCommand global;
    private final OtherSettingsSubCommand other;

    public GuildAndGlobalSettingsCommand(Xeus avaire) {
        super(avaire);
        
        this.server = new ServerSettingsSubCommand(avaire, this);
        this.mod = new ModSettingsSubCommand(avaire, this);
        this.global = new GlobalSettingsSubCommand(avaire, this);
        this.other = new OtherSettingsSubCommand(avaire, this);
    }

    @Override
    public String getName() {
        return "Settings Command";
    }

    @Override
    public String getDescription() {
        return "Modify the guild settings, this is a per rank permission based command and will check your permissions on runtime. Every rank above your own is able to change the permissions that you can change.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command server/mod/global (s/m/g)` - Modify server/moderator/global specific settings.");
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command guild mgi 1` - Set main group ID to 1");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("settings", "configure", "gmanage", "rmanage");
    }

    @Override
    public List<String> getMiddleware() {
        return List.of(
            "isGuildHROrHigher",
            "usedInAdminChannel"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeInfo("I'm missing an argument. Please provide me with any of the following options next time:\n" +
            "- `server` - Modify settings specific to this server.\n" +
            "- `global` - Modify settings specific to the servers connected to the main group of this server " + (context.getGuildSettingsTransformer() != null ? "(:mainGroupId)\n" : "\n") + 
            "- `mod` - Modify the assigned global/local/main group mods within Xeus." + 
            "- `get-level` - Get the permission level of a user within the bot." + 
            "- `other` - These are special commands that won't work out of the box, and require the knowledge of using the bot."
            ).set("mainGroupId", context.getGuildSettingsTransformer().getMainGroupId() != 0 ? context.getGuildSettingsTransformer().getMainGroupId() : "**Group ID has not been set**").queue();
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "server", "s" -> server.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "mod", "moderator", "m" -> mod.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "global", "g" -> global.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "o", "other" -> other.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "get-level" -> getUserLevel(context, context.getGuildSettingsTransformer());
            default -> false;
        };
    }

    private boolean getUserLevel(CommandMessage context, GuildSettingsTransformer guildTransformer) {
        if (context.getMessage().getMentions().getMembers().size() == 1) {
            Member member = context.getMessage().getMentions().getMembers().get(0);
            GuildPermissionCheckType rank = XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, member);
            context.makeInfo(member.getAsMention() + " has permission level ``"
                    + rank.getLevel()
                    + "`` and is classified as a **"
                    + rank.getRankName() + "**")
                    .queue();
            return true;
        }
        if (context.getMessage().getMentions().getMembers().size() > 1) {
            for (Member member : context.getMessage().getMentions().getMembers()) {
                context.makeInfo(member.getAsMention() + " has permission level ``"
                        + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, member).getLevel()
                        + "`` and is classified as a **"
                        + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, member).getRankName() + "**")
                    .queue();
            }
            return true;
        }
        context.makeInfo(context.member.getAsMention() + " has permission level ``"
                + XeusPermissionUtil.getPermissionLevel(context).getLevel() + "`` and is classified as a **"
                + XeusPermissionUtil.getPermissionLevel(context).getRankName() + "**").queue();
        return true;
    }
    
}
