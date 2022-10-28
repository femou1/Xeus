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

package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.changelog.ChangelogHandler;
import com.pinewoodbuilders.changelog.ChangelogMessage;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

public class ChangelogEventAdapter extends EventAdapter {

    public ChangelogEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onMessageDelete(MessageDeleteEvent event) {
        ChangelogHandler.getMessagesMap().remove(event.getMessageIdLong());
    }

    public void onMessageUpdate(MessageUpdateEvent event) {
        createChangelogMessage(event.getMessageIdLong(), event.getMessage());
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        createChangelogMessage(event.getMessageIdLong(), event.getMessage());
    }

    public boolean isChangelogMessage(MessageChannel channel) {
        return channel.getIdLong() == avaire.getConstants().getChangelogChannelId();
    }

    private void createChangelogMessage(long messageId, Message message) {
        ChangelogHandler.getMessagesMap()
            .put(messageId, new ChangelogMessage(message));
    }
}
