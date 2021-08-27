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
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.utilities.ColorUtil;
import com.pinewoodbuilders.utilities.RandomUtil;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.entities.Role;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class SetRoleColorCommand extends Command {

    public SetRoleColorCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Set Role Color Command";
    }

    @Override
    public String getDescription() {
        return "Takes in a role name, followed by a HEX value to set a role to a specific color by simply knowing the HEX value. Additionally users can specify a random color.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <role> random` - Sets the role to a random color",
            "`:command <role> <color>` - Sets the role to the given color provided in hex."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command dj #0000ff` - Sets the dj role blue.",
            "`:command @dj #0000ff` - Sets the dj role blue.",
            "`:command dj random` - Sets the dj role to a random color."
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("setrolecolor", "setrolecolour");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "require:all,general.manage_roles",
            "throttle:guild,1,5"
        );
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "role", "color");
        }

        if (args.length == 1) {
            return sendErrorMessage(context, "errors.missingArgument", "color");
        }

        Role role = RoleUtil.getRoleFromMentionsOrName(context.message, args[0]);
        if (role == null) {
            return sendErrorMessage(context, context.i18n("noRoleFound", args[0]));
        }

        if (!context.getMember().canInteract(role)) {
            return sendErrorMessage(context, context.i18n("userMissingPermissions", role.getAsMention()));
        }

        if (!context.getGuild().getSelfMember().canInteract(role)) {
            return sendErrorMessage(context, context.i18n("botMissingPermissions", role.getAsMention()));
        }

        Color color = !args[1].equalsIgnoreCase("random")
            ? ColorUtil.getColorFromString(args[1].toUpperCase())
            : RandomUtil.getRandomColor();

        if (color == null) {
            return sendErrorMessage(context, context.i18n("invalidColor", args[1]));
        }

        role.getManager().setColor(color).queue();
        context.makeSuccess(context.i18n("colorChangeComplete"))
            .set("role", role.getAsMention())
            .set("color", args[1])
            .queue();

        return true;
    }
}
