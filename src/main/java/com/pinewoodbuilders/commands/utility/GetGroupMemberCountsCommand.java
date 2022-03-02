package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.pinewoodbuilders.utilities.JsonReader.readJsonFromUrl;

public class GetGroupMemberCountsCommand extends Command {

    public GetGroupMemberCountsCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Group Count Command";
    }

    @Override
    public String getDescription() {
        return "Show the amount of members in all roblox groups.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Show the amount of members in all roblox groups."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Show the amount of members in all roblox groups."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("groupcount", "pbcounts");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.MISCELLANEOUS
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        MessageChannel tc = context.channel;
        int PB = getMemberCount(159511);
        int PBST = getMemberCount(645836);
        int PET = getMemberCount(2593707);
        int TMS = getMemberCount(4890641);
        int PBM = getMemberCount(4032816);

        if (tc != null) {
            tc.sendMessage("<a:loading:742658561414266890> Retrieving all group member counts <a:loading:742658561414266890>").queue(pbst -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Xeus.getLogger().error("ERROR: ", e);
                }
                pbst.editMessage("<:PB:757736074641408022> (PB): " + PB + "\n" +
                    "<:PBST:790431720297857024> (PBST): " + PBST + "\n" +
                    "<:TMS:572920815595683841> (TMS): " + TMS + "\n" +
                    "<:PET:694389856071319593> (PET): " + PET + "\n" +
                    "<:pbm:819232629340766219> (PBM): " + PBM).queue();
            });

            return false;
        }
        return false;
    }
    public int getMemberCount(int i) {
        try {
            JSONObject json = readJsonFromUrl("https://groups.roblox.com/v1/groups/" + i);
            return json.getInt("memberCount");
        } catch (Exception e) {
            return 0;
        }
    }
    @Override
    public List <String> getMiddleware() {
        return Collections.singletonList(
            "throttle:user,1,60"
        );
    }


}
