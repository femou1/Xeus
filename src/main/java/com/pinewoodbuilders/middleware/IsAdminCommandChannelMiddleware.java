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

package com.pinewoodbuilders.middleware;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.middleware.Middleware;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.utilities.RestActionUtil;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class IsAdminCommandChannelMiddleware extends Middleware {

    public IsAdminCommandChannelMiddleware(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String buildHelpDescription(@Nonnull CommandMessage context, @Nonnull String[] arguments) {
        return "**This command can only be used in DMs.**";
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        if (!message.getChannelType().isGuild()) {
            return canOnlyBeRunInGuilds(message);
        }

        if (avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong(), true).isAdmin()) {
            return stack.next();
        }

        if (!stack.getDatabaseEventHolder().getGuild().getAdminCommandChannels().contains(message.getChannel().getIdLong())) {
            return invalidChannelErrorMessage(message);
        }

        return stack.next();
    }

    private boolean invalidChannelErrorMessage(@Nonnull Message message) {
        return runMessageCheck(message, () -> {
            MessageFactory.makeError(message, "This command is restricted to specific command channels. due to this, the command will not work.")
                .queue(newMessage -> newMessage.delete().queueAfter(25, TimeUnit.SECONDS), RestActionUtil.ignore);
            return false;
        });
    }

    private boolean canOnlyBeRunInGuilds(@Nonnull Message message) {
        return runMessageCheck(message, () -> {
            MessageFactory.makeError(message, "This command can only be used in guilds.")
                .queue(newMessage -> newMessage.delete().queueAfter(25, TimeUnit.SECONDS), RestActionUtil.ignore);
            return false;
        });
    }
}
