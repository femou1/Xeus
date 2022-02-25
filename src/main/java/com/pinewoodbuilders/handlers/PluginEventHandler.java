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

package com.pinewoodbuilders.handlers;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.handlers.EventHandler;
import com.pinewoodbuilders.plugin.PluginLoader;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class PluginEventHandler extends EventHandler {

    /**
     * Instantiates the event handler and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public PluginEventHandler(Xeus avaire) {
        super(avaire);
    }

    @Override
    public void onGenericEvent(@NotNull GenericEvent event) {
        for (PluginLoader plugin : avaire.getPluginManager().getPlugins()) {
            for (ListenerAdapter listener : plugin.getEventListeners()) {
                listener.onEvent(event);
            }
        }
    }
}
