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

package com.pinewoodbuilders.contracts.commands;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class OnWatchableCommand extends Command {

    /**
     * Creates the given command instance by calling {@link Command#Command(Xeus, boolean)} with allowDM set to true.
     *
     * @param avaire The Xeus class instance.
     */
    public OnWatchableCommand(Xeus avaire) {
        super(avaire);
    }

    /**
     * Creates the given command instance with the given
     * Xeus instance and the allowDM settings.
     *
     * @param avaire  The Xeus class instance.
     * @param allowDM Determines if the command can be used in DMs.
     */
    public OnWatchableCommand(Xeus avaire, boolean allowDM) {
        super(avaire, allowDM);
    }

    /**
     * Gets the name of the role used for muting users, if a valid mute role has been setup for
     * the server, the role will be returned in a mentionable format, however if no valid role
     * have been setup for the server the string "`Muted`" will be returned instead.
     *
     * @param context The command context that should be used to get the muted role name.
     * @return The name of the role used for muting users for the given command context.
     */
    @Nonnull
    protected String getOnWatchRoleFromContext(@Nullable CommandContext context) {
        if (context != null && context.getGuildSettingsTransformer() != null) {
            GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
            if (transformer.getOnWatchRole() != 0) {
                Role muteRole = context.getGuild().getRoleById(transformer.getOnWatchRole());
                if (muteRole != null) {
                    return muteRole.getAsMention();
                }
            }
        }
        return "`On Watch`";
    }
}
