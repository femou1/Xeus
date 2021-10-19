package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.*;

public class GlobalKickCommand extends Command {

    public final ArrayList<String> guilds = new ArrayList<String>() {
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
    public final HashMap<Guild, Role> role = new HashMap<>();
    private final ArrayList<Guild> guild = new ArrayList<>();

    public GlobalKickCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Global Kick Command";
    }

    @Override
    public String getDescription() {
        return "Kick member globally.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command` - Kick a member globally.");
    }

    @Override
    public List<String> getExampleUsage(@Nullable Message message) {
        return Collections.singletonList("`:command` - Kick a member globally.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("global-kick");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList("isPinewoodGuild", "isValidMGMMember");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeError("Sorry, but you didn't give any member id to globally kick!").queue();
            return false;
        }

        if (args.length == 1) {
            context.makeError("Please supply a reason for the global kick!").queue();
            return true;
        }

        if (args.length < 3) {
            context.makeError("Please supply a reason for the global kick!").queue();
            return true;
        }

        if (guild.size() > 0) {
            guild.clear();
        }
        for (String s : guilds) {
            Guild g = avaire.getShardManager().getGuildById(s);
            if (g != null) {
                guild.add(g);
            }
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (reason.endsWith("--pbac-kick")) {
            reason = reason.replace("--pbac-kick", "");
            guild.add(avaire.getShardManager().getGuildById("750471488095780966"));
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

        context.guild
                .kick(context.getGuild().getMemberById("257193596074065921"), "For role removal. Approved by LENEMAR.")
                .queue();

        return true;
    }

}
