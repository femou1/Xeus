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

import com.pinewoodbuilders.chat.MessageType;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.config.YamlConfiguration;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.database.transformers.PlayerTransformer;
import com.pinewoodbuilders.database.transformers.VerificationTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.handlers.DatabaseEventHolder;
import com.pinewoodbuilders.language.I18n;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.internal.requests.DeferredRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandMessage implements CommandContext {

    private static final Logger log = LoggerFactory.getLogger(CommandMessage.class);

    public final Guild guild;
    public final Member member;
    public final MessageChannel channel;
    public final GuildChannel guildChannel;
    public final Message message;

    private final boolean mentionableCommand;
    private final String aliasArguments;
    private final DatabaseEventHolder databaseEventHolder;

    private final CommandContainer container;

    private YamlConfiguration i18n;
    private String i18nCommandPrefix;


    public CommandMessage() {
        this(null);
    }

    public CommandMessage(CommandContext message) {
        this(
            null,
            message == null ? null : message.getDatabaseEventHolder(),
            message == null ? null : message.getMessage(),
            false,
            new String[0]
        );
    }

    public CommandMessage(CommandContext message, DatabaseEventHolder databaseEventHolder) {
        this(null, databaseEventHolder, message.getMessage(), false, new String[0]);
    }

    public CommandMessage(CommandContainer container, DatabaseEventHolder databaseEventHolder, Message message) {
        this(container, databaseEventHolder, message, false, new String[0]);
    }

    public CommandMessage(CommandContainer container, DatabaseEventHolder databaseEventHolder, Message message, boolean mentionableCommand, String[] aliasArguments) {
        if (container != null) {
            setI18nCommandPrefix(container);
        }

        this.message = message;

        boolean isNull = message == null || !message.isFromGuild();

        this.guild = isNull ? null : message.getGuild();
        this.member = isNull ? null : message.getMember();
        this.channel = isNull ? null : message.getChannel();
        this.guildChannel = isNull ? null : message.getGuildChannel();
        this.databaseEventHolder = databaseEventHolder;
        this.container = container;

        this.mentionableCommand = mentionableCommand;
        this.aliasArguments = aliasArguments.length == 0 ?
            null : String.join(" ", aliasArguments);
    }

    public boolean canDelete() {
        return isGuildMessage() && getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE);
    }

    public AuditableRestAction<Void> delete() {
        return canDelete() ? message.delete() : new DeferredRestAction<>(getJDA(), null);
    }

    public JDA getJDA() {
        return message.getJDA();
    }

    public String getContentDisplay() {
        return parseContent(message.getContentDisplay());
    }

    public String getContentStripped() {
        return parseContent(message.getContentStripped());
    }

    public String getContentRaw() {
        String[] parts = message.getContentRaw().split(" ");

        return (aliasArguments == null ? "" : aliasArguments) + String.join(" ",
            Arrays.copyOfRange(parts, isMentionableCommand() ? 2 : 1, parts.length)
        );
    }

    private String parseContent(String content) {
        String[] parts = content.split(" ");

        if (!isMentionableCommand()) {
            return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        }

        int nameSize = (isGuildMessage() ?
            message.getGuild().getSelfMember().getEffectiveName() :
            message.getJDA().getSelfUser().getName()
        ).split(" ").length + 1;

        return String.join(" ", Arrays.copyOfRange(parts, nameSize, parts.length));
    }

    @Override
    public Guild getGuild() {
        return guild;
    }

    @Override
    public Member getMember() {
        return member;
    }

    @Override
    public User getAuthor() {
        return message.getAuthor();
    }

    @Override
    public MessageChannel getChannel() {
        return channel;
    }

    @Override
    public MessageChannel getMessageChannel() {
        return message.getChannel();
    }

    @Override
    public GuildChannel getGuildChannel() {
        return guildChannel;
    }

    @Override
    public Message getMessage() {
        return message;
    }

    @Override
    public GuildTransformer getGuildTransformer() {
        return databaseEventHolder == null ? null : databaseEventHolder.getGuild();
    }

    @Override
    public VerificationTransformer getVerificationTransformer() {
        return databaseEventHolder == null ? null : databaseEventHolder.getVerification();
    }

    @Override
    public PlayerTransformer getPlayerTransformer() {
        return databaseEventHolder == null ? null : databaseEventHolder.getPlayer();
    }

    @Override
    public GuildSettingsTransformer getGuildSettingsTransformer() {
        return databaseEventHolder == null ? null : databaseEventHolder.getGuildSettings();
    }

    @Override
    public DatabaseEventHolder getDatabaseEventHolder() {
        return databaseEventHolder;
    }

    @Override
    public List<User> getMentionedUsers() {
        if (!isMentionableCommand()) {
            return message.getMentions().getUsers();
        }

        List<User> mentions = new ArrayList<>(message.getMentions().getUsers());
        if (!mentions.isEmpty()) {
            mentions.remove(0);
        }
        return mentions;
    }

    @Override
    public List <GuildChannel> getMentionedChannels() {
        return message.getMentions().getChannels();
    }

    @Override
    public boolean isMentionableCommand() {
        return mentionableCommand;
    }

    @Override
    public boolean mentionsEveryone() {
        return message.getMentions().mentionsEveryone()
            || message.getContentRaw().contains("@everyone")
            || message.getContentRaw().contains("@here");
    }

    @Override
    public boolean isGuildMessage() {
        return message.isFromGuild();
    }

    @Override
    public boolean canTalk() {
        if (!isGuildMessage()) {
            return true;
        }

        return message.getGuild().getSelfMember().hasPermission(message.getGuildChannel(),
            Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL, Permission.MESSAGE_EMBED_LINKS
        );
    }

    public CommandContainer getContainer() {
        return container;
    }

    public PlaceholderMessage makeError(String message) {
        return MessageFactory.makeError(this.message, message);
    }

    public PlaceholderMessage makeWarning(String message) {
        return MessageFactory.makeWarning(this.message, message);
    }

    public PlaceholderMessage makeSuccess(String message) {
        return MessageFactory.makeSuccess(this.message, message);
    }

    public PlaceholderMessage makeInfo(String message) {
        return MessageFactory.makeInfo(this.message, message);
    }

    public PlaceholderMessage makeEmbeddedMessage(Color color, String message) {
        return MessageFactory.makeEmbeddedMessage(this.message, color, message);
    }

    public PlaceholderMessage makeEmbeddedMessage(MessageType type, MessageEmbed.Field... fields) {
        return makeEmbeddedMessage(type.getColor(), fields);
    }

    public PlaceholderMessage makeEmbeddedMessage(Color color, MessageEmbed.Field... fields) {
        return MessageFactory.makeEmbeddedMessage(this.message.getChannel(), color, fields);
    }

    public PlaceholderMessage makeEmbeddedMessage() {
        return MessageFactory.makeEmbeddedMessage(this.message.getChannel());
    }

    @Override
    @Nonnull
    public YamlConfiguration getI18n() {
        if (this.i18n == null) {
            this.i18n = I18n.get(getGuild());
        }
        return this.i18n;
    }

    public CommandMessage setI18n(YamlConfiguration i18n) {
        this.i18n = i18n;
        return this;
    }

    @Override
    @CheckReturnValue
    public String i18n(@Nonnull String key) {
        if (i18nCommandPrefix != null) {
            key = i18nCommandPrefix + "." + key;
        }
        return i18nRaw(key);
    }

    @Override
    public String i18n(@Nonnull String key, Object... args) {
        if (i18nCommandPrefix != null) {
            key = i18nCommandPrefix + "." + key;
        }
        return i18nRaw(key, args);
    }

    @Override
    @CheckReturnValue
    public String i18nRaw(@Nonnull String key) {
        if (getI18n().contains(key)) {
            return getI18n().getString(key)
                .replace("\\n", "\n")
                .replace("\\t", "\t");
        } else {
            log.warn("Missing language entry for key {} in language {}", key, I18n.getLocale(getGuild()).getLanguage().getCode());
            return I18n.getDefaultLanguage().getConfig().getString(key)
                .replace("\\n", "\n")
                .replace("\\t", "\t");
        }
    }

    @Override
    public String i18nRaw(@Nonnull String key, Object... args) {
        String message = i18nRaw(key);
        if (message == null) {
            return null;
        }
        return I18n.format(message, args);
    }

    @Override
    public void setI18nPrefix(@Nullable String i18nPrefix) {
        this.i18nCommandPrefix = i18nPrefix;
    }

    @Override
    public String getI18nCommandPrefix() {
        return i18nCommandPrefix;
    }

    @Override
    public void setI18nCommandPrefix(@Nonnull CommandContainer container) {
        setI18nPrefix(
            container.getCategory().getName().toLowerCase() + "."
                + container.getCommand().getClass().getSimpleName()
        );
    }
}
