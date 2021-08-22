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

package com.pinewoodbuilders.contracts.commands;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandPriority;

import java.util.Collections;
import java.util.List;

public abstract class SystemCommand extends Command {

    /**
     * Creates the given command instance by calling {@link #SystemCommand(Xeus, boolean)} with allowDM set to true.
     *
     * @param avaire The Xeus class instance.
     */
    public SystemCommand(Xeus avaire) {
        this(avaire, true);
    }

    /**
     * Creates the given command instance with the given
     * Xeus instance and the allowDM settings.
     *
     * @param avaire  The Xeus class instance.
     * @param allowDM Determines if the command can be used in DMs.
     */
    public SystemCommand(Xeus avaire, boolean allowDM) {
        super(avaire, allowDM);
    }

    @Override
    public List<String> getMiddleware() {
        if (!getCommandPriority().equals(CommandPriority.SYSTEM_ROLE)) {
            return Collections.singletonList("isBotAdmin");
        }
        return Collections.singletonList("isBotAdmin:use-role");
    }

    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.SYSTEM;
    }
}
