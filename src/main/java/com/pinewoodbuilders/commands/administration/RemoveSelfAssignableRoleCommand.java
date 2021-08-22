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
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.CacheFingerprint;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CacheFingerprint(name = "self-assignable-role-command")
public class RemoveSelfAssignableRoleCommand extends Command {

    public RemoveSelfAssignableRoleCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Remove Self Assignable Role Command";
    }

    @Override
    public String getDescription() {
        return "Removes a role from the self-assignable roles list, any role on the list can be claimed by users when they use `:prefixiam <role>`.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command <role>` - Removes the mentioned role from the self-assignable roles list.");
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command DJ`");
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Arrays.asList(
            AddSelfAssignableRoleCommand.class,
            ListSelfAssignableRolesCommand.class
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("rsar");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "require:user,general.administrator",
            "throttle:guild,1,5"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.ROLE_ASSIGNMENTS);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "role");
        }

        String roleName = String.join(" ", args);
        Role role = RoleUtil.getRoleFromMentionsOrName(context.getMessage(), roleName);
        if (role == null) {
            context.makeWarning(context.i18nRaw("" +
                "administration.common.invalidRole"))
                .set("role", roleName)
                .queue();
            return false;
        }

        if (!RoleUtil.canUserInteractWithRole(context.getMessage(), role)) {
            return false;
        }

        try {
            transformer.getSelfAssignableRoles().remove(role.getId());
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> {
                    statement.set("claimable_roles", Xeus.gson.toJson(transformer.getSelfAssignableRoles()));
                });

            context.makeSuccess(context.i18n("message"))
                .set("slots", transformer.getType().getLimits().getSelfAssignableRoles() - transformer.getSelfAssignableRoles().size())
                .set("role", role.getName()).queue();

            return true;
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
        return false;
    }
}
