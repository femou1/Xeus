package com.pinewoodbuilders.middleware.global;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.contracts.middleware.Middleware;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.middleware.MiddlewareStack;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;

public class LogModeratorCommand extends Middleware {


    public LogModeratorCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        GuildSettingsTransformer settings = stack.getDatabaseEventHolder().getGuildSettings();
        if (!message.isFromGuild()) return stack.next();
        if (message.getMember() == null) return stack.next();
        boolean isMod = XeusPermissionUtil.getPermissionLevel(settings, message.getGuild(), message.getMember()).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_HR.getLevel();
        if (!isMod) return stack.next();

        if (settings != null && settings.getGlobalSettings() != null && settings.getGlobalSettings().getModerationServerId() != 0) {
            Guild moderationGuild = avaire.getShardManager().getGuildById(settings.getGlobalSettings().getModerationServerId());
            if (moderationGuild != null) {
                List<TextChannel> channels = moderationGuild.getTextChannelsByName("command-logs", true);
                if (channels.size() > 0) {
                    EmbedBuilder embed = new EmbedBuilder();
                    PlaceholderMessage messageToSend = new PlaceholderMessage(channels.get(0), embed,
                        """
                            **User:** :user
                            **Command:** `:command`
                            **Channel:** :channel (`:channel-name`)
                            **Guild:** `:guild`
                            **Full Message:** ```:message```
                                """)
                        .set("user", message.getMember().getEffectiveName())
                        .set("command", stack.getCommand().getName())
                        .set("channel", "<#" + message.getChannel().getIdLong() + ">")
                        .set("channel-name", "#" + message.getChannel().getName())
                        .set("guild", message.getGuild().getName())
                        .set("message", message.getContentRaw())
                        .setThumbnail(message.getMember().getUser().getEffectiveAvatarUrl())
                        .setColor(message.getMember().getColor())
                        .setFooter("User ID: " + message.getMember().getUser().getIdLong(), message.getGuild().getIconUrl())
                        .setTimestamp(Instant.now());

                    channels.get(0).sendMessageEmbeds(messageToSend.buildEmbed()).queue();
                }
            }
        }
        return stack.next();
    }


}

