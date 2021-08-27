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

package com.pinewoodbuilders.plugin.translators;

import com.pinewoodbuilders.contracts.plugin.Plugin;
import com.pinewoodbuilders.plugin.PluginHolder;
import com.pinewoodbuilders.plugin.PluginRepository;

import java.util.List;

public class PluginHolderTranslator implements Plugin {

    private final PluginHolder holder;

    /**
     * Creates a new plugin translator holder instance
     * using the given plugin holder.
     *
     * @param holder The plugin holder instance.
     */
    public PluginHolderTranslator(PluginHolder holder) {
        this.holder = holder;
    }

    @Override
    public String getName() {
        return holder.getName();
    }

    @Override
    public String getDescription() {
        return holder.getDescription();
    }

    @Override
    public List<String> getAuthors() {
        return holder.getAuthors();
    }

    @Override
    public PluginRepository getRepository() {
        return holder.getRepository();
    }

    @Override
    public boolean isInstalled() {
        return false;
    }
}
