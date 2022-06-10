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

package com.pinewoodbuilders.commands;

import com.pinewoodbuilders.config.YamlConfiguration;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.database.transformers.PlayerTransformer;
import com.pinewoodbuilders.database.transformers.VerificationTransformer;
import com.pinewoodbuilders.handlers.DatabaseEventHolder;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class FakeCommandMessage implements CommandContext {

    @Override
    public Guild getGuild() {
        return null;
    }

    @Override
    public Member getMember() {
        return null;
    }

    @Override
    public User getAuthor() {
        return null;
    }

    @Override
    public MessageChannel getChannel() {
        return null;
    }

    @Override
    public MessageChannel getMessageChannel() {
        return null;
    }

    @Override
    public GuildChannel getGuildChannel() {
        return null;
    }

    @Override
    public Message getMessage() {
        return null;
    }

    @Override
    public GuildTransformer getGuildTransformer() {
        return null;
    }

    @Override
    public VerificationTransformer getVerificationTransformer() {
        return null;
    }

    @Override
    public PlayerTransformer getPlayerTransformer() {
        return null;
    }

    @Override
    public DatabaseEventHolder getDatabaseEventHolder() {
        return null;
    }

    @Override
    public GuildSettingsTransformer getGuildSettingsTransformer() {
        return null;
    }


    @Override
    public List<User> getMentionedUsers() {
        return new ArrayList<>();
    }

    @Override
    public List <GuildChannel> getMentionedChannels() {
        return new ArrayList<>();
    }

    @Override
    public boolean isMentionableCommand() {
        return false;
    }

    @Override
    public boolean mentionsEveryone() {
        return false;
    }

    @Override
    public boolean isGuildMessage() {
        return false;
    }

    @Override
    public boolean canTalk() {
        return true;
    }

    @Nonnull
    @Override
    public YamlConfiguration getI18n() {
        throw new UnsupportedOperationException("Invoking the i18n method on a fake command message context is not supported!");
    }

    @Override
    public String i18n(@Nonnull String key) {
        return "fake-" + key;
    }

    @Override
    public String i18n(@Nonnull String key, Object... args) {
        return i18n(key);
    }

    @Override
    public String i18nRaw(@Nonnull String key) {
        return "fake-" + key;
    }

    @Override
    public String i18nRaw(@Nonnull String key, Object... args) {
        return i18nRaw(key);
    }

    @Override
    public void setI18nPrefix(@Nullable String i18nPrefix) {
        // This does nothing
    }

    @Override
    public String getI18nCommandPrefix() {
        return "fake-prefix";
    }

    @Override
    public void setI18nCommandPrefix(@Nonnull CommandContainer container) {
        // This does nothing
    }

    
}
