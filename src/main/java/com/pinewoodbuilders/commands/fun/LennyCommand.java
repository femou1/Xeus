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

package com.pinewoodbuilders.commands.fun;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.utilities.RandomUtil;

import java.util.Collections;
import java.util.List;

public class LennyCommand extends Command {

    public LennyCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Lenny command";
    }

    @Override
    public String getDescription() {
        return "( ͡° ͜ʖ ͡°)";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command` - ( ͡° ͜ʖ ͡°)");
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("( ͡° ͜ʖ ͡°)");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("lenny");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (RandomUtil.getInteger(25) > 0) {
            context.getMessageChannel().sendMessage("( ͡° ͜ʖ ͡°)").queue();
            return true;
        }

        context.getMessageChannel().sendMessage("( ͡° ͜ʖ ( ͡° ͜ʖ ( ͡° ͜ʖ ( ͡° ͜ʖ ͡°) ͜ʖ ͡°)ʖ ͡°)ʖ ͡°)").queue();
        return true;
    }
}
