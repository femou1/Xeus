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

import java.util.Collections;
import java.util.List;

public class FlipTextCommand extends Command {

    private static final String NORMAL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_,;.?!/\\'0123456789";
    private static final String FLIPPED = "∀qϽᗡƎℲƃHIſʞ˥WNOԀὉᴚS⊥∩ΛMXʎZɐqɔpǝɟbɥıظʞןɯuodbɹsʇnʌʍxʎz‾'؛˙¿¡/\\,0ƖᄅƐㄣϛ9ㄥ86 ";

    public FlipTextCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Flip Text Command";
    }

    @Override
    public String getDescription() {
        return "Flips the given message upside down.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command <message>` - Flips the given message.");
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command This is some random message`");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("flip");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "message");
        }

        String string = String.join(" ", args);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char letter = string.charAt(i);

            int a = NORMAL.indexOf(letter);
            builder.append((a != -1) ? FLIPPED.charAt(a) : letter);
        }

        context.makeInfo(builder.toString()).queue();
        return true;
    }
}
