/*
 * Copyright (c) 2019.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.commands.administration;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.utilities.MentionableUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RoleInfoCommand extends Command {

    private final static String LINESTART = " ▶ " + " ";
    private final static String ROLE_EMOJI = "\uD83C\uDFAD"; // 🎭


    public RoleInfoCommand(Xeus avaire) {
        super(avaire, false);
    }

    public static String escapeMentions(@NotNull String string) {
        return string.replace("@everyone", "@\u0435veryone")
            .replace("@here", "@h\u0435re")
            .replace("discord.gg/", "dis\u0441ord.gg/");
    }

    @Override
    public String getName() {
        return "Role Info Command";
    }

    @Override
    public String getDescription() {
        return "See information about a specific role within the discord it's ran in.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <role>` - View information about a role."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command Trooper/@Trooler` - View information about the Trooper role.");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("roleinfo", "ri");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "throttle:user,1,5"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        Role role;
        List<Role> rolesByMention = (context.getMessage().getMentions().getRoles());

        if (rolesByMention.isEmpty()) {
            Role rolesByName = MentionableUtil.getRole(context.getMessage(), args);
            if (rolesByName == null) {
                context.makeError("No Matching roles found!").queue();
                return false;
            }
            role = rolesByName;
        } else
            role = rolesByMention.get(0);

            final String title = (ROLE_EMOJI + " Roleinfo: " + escapeMentions(role.getName()) + ":");
            Color color = role.getColor();
    
        StringBuilder description = new StringBuilder(""
            + LINESTART + "ID: **" + role.getId() + "**\n"
            + LINESTART + "Creation: **" + role.getTimeCreated().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "**\n"
            + LINESTART + "Position: **" + role.getPosition() + "**\n"
            + LINESTART + "Color: **#" + (color == null ? "000000" : Integer.toHexString(color.getRGB()).toUpperCase().substring(2)) + "**\n"
            + LINESTART + "Mentionable: **" + role.isMentionable() + "**\n"
            + LINESTART + "Hoisted: **" + role.isHoisted() + "**\n"
            + LINESTART + "Managed: **" + role.isManaged() + "**\n"
            + LINESTART + "Public Role: **" + (role.isPublicRole() ? "✅" : "❌") + "**\n"
            + LINESTART + "Members: **" + getMembersWithRole(role, context.getGuild()) + "**\n"
            + LINESTART + "Permissions: \n"
        );

        if (role.getPermissions().isEmpty()) {
            description.append("No permissions set");
        } else {
            description.append(role.getPermissions().stream().map(p -> "`, `" + p.getName()).reduce("", String::concat)
                .substring(3)).append("`");
        }

        PlaceholderMessage eb = context.makeEmbeddedMessage()
            .setColor(color)
            .setDescription(description.toString())
            .setTitle(title);

        eb.queue();
        return true;
    }

    private String getMembersWithRole(Role role, Guild guild) {
        int membersWithRole = 0;
        for (Member m : guild.getMembers()) {
            if (m.getRoles().contains(role)) {
                membersWithRole++;
            }
        }

        if (membersWithRole > 0) {
            return String.valueOf(membersWithRole);
        }
        return "No members found";
    }

}
