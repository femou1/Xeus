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
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModifyRoleChannelLockCommand extends Command {

    private static final Logger log = LoggerFactory.getLogger(ModifyRoleChannelLockCommand.class);

    public ModifyRoleChannelLockCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Role Channel Lock Command";
    }

    @Override
    public String getDescription() {
        return "This command is used to modify the roles that get removed from the channels in the ``";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <role> [status]` - Toggles the locking feature on/off."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command` - Lists all the roles that currently has their XP status disabled."
        );
    }

    @Override
    public List <Class <? extends Command>> getRelations() {
        return Arrays.asList(
            ModifyLockChannelCommand.class,
            LockChannelsCommand.class
        );
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("rolelock", "rlock");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "isGuildLeadership",
            "throttle:user,1,5"
        );
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.CHANNEL_LOCK);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer guildTransformer = context.getGuildTransformer();
        if (args.length == 0 || NumberUtil.parseInt(args[0], -1) > 0) {
            return sendEnabledRoles(context, guildTransformer);
        }

        Role role = MentionableUtil.getRole(context.getMessage(), args);
        if (role == null) {
            return sendErrorMessage(context, context.i18n("invalidRole", args[0]));
        }

        if (args.length > 1) {
            return handleToggleRole(context, role, ComparatorUtil.getFuzzyType(args[1]));
        }
        return handleToggleRole(context, role, ComparatorUtil.ComparatorType.UNKNOWN);
    }

    private boolean sendEnabledRoles(CommandMessage context, GuildTransformer transformer) {
        if (transformer.getLockableChannelRoles().isEmpty()) {
            return sendErrorMessage(context, context.i18n("noRolesWithRewardsDisabled",
                generateCommandTrigger(context.getMessage())
            ));
        }

        List <String> roles = new ArrayList <>();
        for (Long roleId : transformer.getLockableChannelRoles()) {
            Role role = context.getGuild().getRoleById(roleId);
            if (role != null) {
                roles.add(role.getAsMention());
            }
        }

        context.makeInfo(context.i18n("listRoles"))
            .set("roles", String.join(", ", roles))
            .setTitle(context.i18n("listRolesTitle",
                transformer.getLockableChannelRoles().size()
            ))
            .queue();

        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean handleToggleRole(CommandMessage context, Role role, ComparatorUtil.ComparatorType value) {
        GuildTransformer guildTransformer = context.getGuildTransformer();

        switch (value) {
            case FALSE:
                guildTransformer.getLockableChannelRoles().remove(role.getIdLong());
                break;

            case TRUE:
                guildTransformer.getLockableChannelRoles().add(role.getIdLong());
                break;

            case UNKNOWN:
                if (guildTransformer.getLockableChannelRoles().contains(role.getIdLong())) {
                    guildTransformer.getLockableChannelRoles().remove(role.getIdLong());
                } else {
                    guildTransformer.getLockableChannelRoles().add(role.getIdLong());
                }
                break;
        }

        boolean isEnabled = guildTransformer.getLockableChannelRoles().contains(role.getIdLong());

        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> {
                    statement.set("lockable_channels_roles", Xeus.gson.toJson(
                        guildTransformer.getLockableChannelRoles()
                    ), true);
                });

            context.makeSuccess(context.i18n("success"))
                .set("role", role.getAsMention())
                .set("status", context.i18n(isEnabled ? "status.enabled" : "status.disabled"))
                .queue();

            return true;
        } catch (SQLException e) {
            log.error("Failed to save the level exempt roles to the database for guild {}, error: {}",
                context.getGuild().getId(), e.getMessage(), e
            );

            context.makeError("Failed to save the changes to the database, please try again. If the issue persists, please contact one of my developers.").queue();

            return false;
        }
    }
}
