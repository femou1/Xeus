package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DivisionsCommand extends Command {
    public DivisionsCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Divisions Command";
    }

    @Override
    public String getDescription() {
        return "Shows into about the different groups in PBST.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Show's the discords and roblox groups."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Show's the discords and roblox groups."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Arrays.asList("division", "groups");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.MISCELLANEOUS
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "throttle:user,1,120",
            "throttle:guild,1,60"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        context.getChannel().sendMessageEmbeds(
            context.makeEmbeddedMessage().setDescription("__**Official Pinewood Groups**__\n\n" +
                "**Pinewood Builders**\n" +
                "Welcome, Pinewood is where we create cool new things and roleplay, be part of our ever growing community and experience our huge interactive games!\n" +
                " - [Unofficial website (Made by Stefano and community)](https://pinewood-builders.com)\n" +
                " - [Discord](https://discord.gg/RHWxvhc)\n" +
                " - [Roblox Group](https://www.roblox.com/groups/159511/Pinewood-Builders#!/about)\n" +
                " - [Steam Group](https://steamcommunity.com/groups/pinewoodbuilders)\n\n" +
                "**Pinewood Builders Security Team**: \n" +
                "We band together as a team, together we are an unstoppable force.\n" +
                " - [Unfficial Handbook (Made by Stefano and community)](https://pbst.pinewood-builders.com/)\n" +
                " - [Discord](https://discord.gg/DDUzTwM) \n" +
                " - [Roblox Group](https://roblox.com/groups/645836/Pinewood-Builders-Security-Team#!/about)\n\n" +
                "**Pinewood Emergency Team**:\n" +
                "An elite team of the best rescue heroes and firefighters responding to emergencies all over Roblox and the world!\n" +
                " - [Discord](https://discord.gg/t4KBPkM)\n" +
                " - [Roblox Group](https://www.roblox.com/groups/2593707/Pinewood-Emergency-Team#!/about)\n\n" +
                "**The Mayhem Syndicate**:\n" +
                "Glory to the syndicate.\n" +
                " - [Unofficial Handbook (Made by Stefano and community)](https://tms.pinewood-builders.com/)\n" +
                " - [Discord](https://discord.gg/3axZ5tb)\n" +
                " - [Roblox Group](https://www.roblox.com/groups/4890641/The-Mayhem-Syndicate#!/about)\n\n" +
                "**Pinewood Builders Quality Assurance**:\n" +
                "We're a group of testers that ensure that new and existing development has as little bugs as possible and works as intended. Join us today to become the first to test Pinewood game mechanics.\n" +
                " - [Discord](https://discord.gg/6XVM5gc)\n" +
                " - [Roblox Group](https://www.roblox.com/groups/4543796/Pinewood-Builders-Quality-Assurance#!/about)\n").buildEmbed(),
            context.makeEmbeddedMessage().setDescription("**Pinewood Builders Aerospace**:\n" +
                "The aerospace division of Pinewood Builders, combined with our space division, shuttle, aircraft and military flight as well as astronaut centrifuge training at our research facility.\n" +
                " - [Roblox Group](https://www.roblox.com/groups/926624/Pinewood-Builders-Aerospace#!/about)\n" +
                "\n" +
                "**Pinewood Builders Media**\n" +
                "Create cool videos for Pinewood! Want to host a news show? A YouTube video with a funny montage at a Pinewood facility? Create music? This is the place to do it! You can earn a promotion from doing any of these to a respective rank.\n" +
                " - [Discord](https://discord.gg/yTVFKne)\n" +
                " - [Roblox Group](https://www.roblox.com/groups/4032816/Pinewood-Builders-Media#!/about)\n" +
                "\n" +
                "**Pinewood Mega Miners Fanclub**\n" +
                "A group for Mega Miners, a subsidiary group of Pinewood- handling the mining and resource extraction sector of Pinewood Builders, as well as discussion and suggestions.\n" +
                " - [Roblox Group](https://www.roblox.com/groups/1062766/Mega-Miners#!/about)\n" +
                "\n").buildEmbed()
        ).queue();

        return true;
    }
}
