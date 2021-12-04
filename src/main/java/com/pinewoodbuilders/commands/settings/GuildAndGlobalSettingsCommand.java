package com.pinewoodbuilders.commands.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.global.GlobalSettingsSubCommand;
import com.pinewoodbuilders.commands.settings.moderation.ModSettingsSubCommand;
import com.pinewoodbuilders.commands.settings.server.ServerSettingsSubCommand;
import com.pinewoodbuilders.commands.settings.other.OtherSettingsSubCommand;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;

import net.dv8tion.jda.api.entities.Member;

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
        return Arrays.asList("settings", "configure", "gmanage");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeInfo("I'm missing an argument. Please provide me with any of the following options next time:\n" +
            "- `server` - Modify settings specific to this server.\n" +
            "- `global` - Modify settings specific to the servers connected to the main group of this server " + (context.getGuildSettingsTransformer() != null ? "(:mainGroupId)\n" : "\n") + 
            "- `mod` - Modify the assigned global/local/main group mods within Xeus." + 
            "- `get-level` - Get the permission level of a user within the bot." + 
            "- `other` - These are special commands that won't work out of the box, and require the knowladge of using the bot."
            ).set("mainGroupId", context.getGuildSettingsTransformer().getMainGroupId() != 0 ? "(`:mainGroupId`)" : "**Group ID has not been set**").queue();
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "server":
            case "s":
                return server.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "mod":
            case "moderator":
            case "m":
                return mod.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "global":
            case "g": 
                return global.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "o":
            case "other":
                return other.onCommand(context, Arrays.copyOfRange(args, 1, args.length));
            case "get-level":
                return getUserLevel(context, context.getGuildSettingsTransformer());
        }
        return false;
    }

    private boolean getUserLevel(CommandMessage context, GuildSettingsTransformer guildTransformer) {
        if (context.getMessage().getMentionedMembers().size() == 1) {
            Member m = context.getMessage().getMentionedMembers().get(0);
            context.makeInfo(m.getAsMention() + " has permission level ``"
                    + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, m).getLevel()
                    + "`` and is classified as a **"
                    + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, m).getRankName() + "**")
                    .queue();
            return true;
        }
        if (context.getMessage().getMentionedMembers().size() > 1) {
            context.getMessage().getMentionedMembers().forEach(member -> {
                context.makeInfo(member.getAsMention() + " has permission level ``"
                    + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, member).getLevel()
                    + "`` and is classified as a **"
                    + XeusPermissionUtil.getPermissionLevel(guildTransformer, context.guild, member).getRankName() + "**")
                    .queue();
            });
            return true;
        }
        context.makeInfo(context.member.getAsMention() + " has permission level ``"
                + XeusPermissionUtil.getPermissionLevel(context).getLevel() + "`` and is classified as a **"
                + XeusPermissionUtil.getPermissionLevel(context).getRankName() + "**").queue();
        return true;
    }
    
}
