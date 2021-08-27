package com.pinewoodbuilders.commands.globalmod;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.collection.Collection;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;

public class GlobalUnbanCommand extends Command {

    public GlobalUnbanCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Global Unban Command";
    }

    @Override
    public String getDescription() {
        return "Unban member globally.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <user id>` - Unban a member globally.");
    }

    @Override
    public List <String> getExampleUsage(@Nullable Message message) {
        return Collections.singletonList(
            "`:command 1829283649274938104` - Unban a member globally (By ID).");
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("global-unban");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isOfficialPinewoodGuild",
            "isValidPIAMember"
        );
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    public final ArrayList <String> guilds = new ArrayList <String>() {{
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
    }};


    public final HashMap <Guild, Role> role = new HashMap <>();
    private final ArrayList <Guild> guild = new ArrayList <>();

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length < 1) {
            context.makeError("Sorry, but you didn't give any member id to globbaly unban!").queue();
            return true;
        }
        if (args.length > 1) {
            context.makeError("Sorry, but you can only globally unban 1 member at a time!").queue();
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
        StringBuilder sb = new StringBuilder();
        for (Guild g : guild) {
            g.unban(args[0]).reason("Global unban, executed by: " + context.member.getEffectiveName()).queue();
            sb.append("``").append(g.getName()).append("`` - :white_check_mark:\n");
        }
        context.makeSuccess("<@" + args[0] + "> has been unbanned from: \n\n" + sb).queue();

        TextChannel tc = avaire.getShardManager().getTextChannelById(Constants.PIA_LOG_CHANNEL);
        if (tc != null) {
            tc.sendMessageEmbeds(context.makeInfo("[``:global-unbanned-id`` was unbanned from all discords by :user](:link)").set("global-unbanned-id", args[0]).set("user", context.getMember().getAsMention()).set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
        }



        try {
            handleGlobalPermUnban(context, args);
        } catch (SQLException exception) {
            Xeus.getLogger().error("ERROR: ", exception);
            context.makeError("Something went wrong adding this user to the global perm ban database.").queue();
        }

        return true;
    }

    private void handleGlobalPermUnban(CommandMessage context, String[] args) throws SQLException {
        Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("userId", args[0]).get();
        if (c.size() > 0) {
            avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("userId", args[0]).delete();
            context.makeError("**:userId** has been removed from the anti-unban system!").set("userId", args[0]).queue();
        } else {
            context.makeError("This user is not banned!").queue();
        }
    }
}

