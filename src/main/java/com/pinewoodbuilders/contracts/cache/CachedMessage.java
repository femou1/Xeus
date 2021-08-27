package com.pinewoodbuilders.contracts.cache;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.OffsetDateTime;
import java.util.List;

public class CachedMessage
{
    private final long id;
    private final CachedUser author;
    private final OffsetDateTime timeCreated;
    private final String contentRaw;
    private final String channelId;
    private final String attachment;
    private final boolean pinned;
    private final List<MessageEmbed> embedList;

    public CachedMessage(Message message)
    {
        this.id = message.getIdLong();
        this.author = new CachedUser(message.getAuthor());
        this.timeCreated = message.getTimeCreated();
        this.contentRaw = message.getContentRaw();
        this.channelId = message.getChannel().getId();
        this.attachment = message.getAttachments().size() == 1 ? message.getAttachments().get(0).getUrl() : null;
        this.pinned = message.isPinned();
        this.embedList = message.getEmbeds();
    }

    public long getIdLong() {
        return id;
    }

    public CachedUser getAuthor() {
        return author;
    }

    public OffsetDateTime getTimeCreated() {
        return timeCreated;
    }

    public String getContentRaw() {
        return contentRaw;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getAttachment() {
        return attachment;
    }

    public boolean isPinned() {
        return pinned;
    }

    public List<MessageEmbed> getEmbedList() {
        return embedList;
    }
}
