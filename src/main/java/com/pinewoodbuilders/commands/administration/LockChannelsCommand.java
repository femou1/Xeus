/*
 * Copyright (c) 2018.
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
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import java.util.*;

public class LockChannelsCommand extends Command {


    public LockChannelsCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Lock Channel(s) Command";
    }

    @Override
    public String getDescription() {
        return "Lock one, or all channels.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command (all)` - Lock the channel you executed the command in."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command #spam off` - Disables lock in the #spam channel.",
            "`:command #sandbox` - Toggles the lock on/off for the #sandbox channel.",
            "`:command` - Lists all the channels that currently have their lock-ability enabled."
        );
    }

    @Override
    public List <Class <? extends Command>> getRelations() {
        return Arrays.asList(
            ModifyRoleChannelLockCommand.class,
            ModifyLockChannelCommand.class
        );
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("lock", "lc");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "isGuildHROrHigher",
            "throttle:user,1,5"
        );
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.CHANNEL_LOCK);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public boolean onCommand(CommandMessage context, String[] args) {
        List <Role> r = new ArrayList <>();
        context.getGuildTransformer().getLockableChannelRoles().forEach(v -> {
            Role role = context.getGuild().getRoleById(v);
            if (role != null) {
                r.add(role);
            }
        });

        if (args.length > 0 && context.getMentionedChannels().size() < 1) {
            if (args[0].equals("all")) {
                if (!(XeusPermissionUtil.getPermissionLevel(context).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel())) {
                    context.makeError("Sorry, but you have to be a MGM member, or a Admin+ to use this command!").queue();
                    return false;
                }
                List <TextChannel> c = new ArrayList <>();
                context.getGuildTransformer().getLockableChannels().forEach(v -> {
                    TextChannel channel = context.getGuild().getTextChannelById(v);
                    if (channel != null) {
                        c.add(channel);
                    }
                });
                return handleGlobalChannelLock(context, r, c);
            }
        }
        return handleChannelLock(context, r);
    }

    private boolean handleGlobalChannelLock(CommandMessage context, List <Role> r, List <TextChannel> c) {
        StringBuilder sb = new StringBuilder();
        for (TextChannel tc : c) {
            changePermissions(r, sb, tc, context);
        }
        context.makeSuccess("Succesfully modified the current channels!\n" + sb).queue();
        return true;
    }

    private boolean handleChannelLock(CommandMessage context, List <Role> r) {
        StringBuilder sb = new StringBuilder();
        GuildChannel tc = context.getMentionedChannels().size() == 1 ? context.getMentionedChannels().get(0) : context.getGuildChannel();
        changePermissions(r, sb, tc, context);
        context.makeSuccess("Succesfully modified the current channel!\n" + sb).queue();
        return true;
    }

    EnumSet <Permission> allow_see = EnumSet.of(Permission.VIEW_CHANNEL);
    EnumSet <Permission> deny_write = EnumSet.of(Permission.MESSAGE_SEND);

    private void changePermissions(List <Role> r, StringBuilder sb, GuildChannel tc, CommandMessage context) {
        if (tc != null) {
            for (Role role : r) {
                PermissionOverride permissionOverride = tc.getPermissionContainer().getPermissionOverride(role);
                if (permissionOverride != null) {
                    if (permissionOverride.getRole().hasPermission(tc, Permission.MESSAGE_SEND)) {
                        permissionOverride.getManager().setPermissions(allow_see, deny_write).queue();
                        sb.append(":x: ").append(tc.getAsMention()).append(": ").append(role.getAsMention()).append("\n");
                    } else {
                        permissionOverride.getManager().clear(Permission.MESSAGE_SEND).setAllowed(Permission.VIEW_CHANNEL).queue();
                        sb.append(":white_check_mark: ").append(tc.getAsMention()).append(": ").append(role.getAsMention()).append("\n");
                    }
                }
            }
        }

    }

}

