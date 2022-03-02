package com.pinewoodbuilders.scheduler.tasks;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.time.Carbon;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;

import java.awt.*;
import java.sql.SQLException;

public class SendRemindersTask implements Task
{
    /**
     * Handles the task when the task is ready to be invoked.
     *
     * @param avaire The AvaIre class instance.
     */
    @Override
    public void handle(Xeus avaire) {
        if (!avaire.areWeReadyYet()) {
            return;
        }

        try
        {
            Collection collection = avaire.getDatabase().newQueryBuilder(Constants.REMINDERS_TABLE_NAME)
                .useAsync(true)
                .where("sent", false)
                .andWhere("expires_at","<=", Carbon.now().addSecond())
                .get();

            for (DataRow datarow:
                collection)
            {
                int id = datarow.getInt("id");
                User user = avaire.getShardManager().getUserById(datarow.getString("user_id"));
                GuildChannel tc = datarow.getString("channel_id") != null
                    ? avaire.getShardManager().getGuildChannelById(datarow.getString("channel_id")) : null;

                String content = datarow.getString("message");
                Carbon timeStamp = datarow.getTimestamp("stored_at");

                if (tc instanceof MessageChannel textChannel) {
                    if (!textChannel.canTalk()) {
                        sendPrivateMessage(avaire,user,content,timeStamp,id);
                    }
                    else
                    {
                        textChannel.sendMessage(
                            buildMessage(user,content,timeStamp)
                        ).queue(
                            success -> markMessageSent(avaire, id));
                        ;
                    }
                } else {
                    sendPrivateMessage(avaire,user,content,timeStamp,id);
                }
            }

            avaire.getDatabase().newQueryBuilder(Constants.REMINDERS_TABLE_NAME)
                .useAsync(true)
                .where("sent","1")
                .delete();

        }
        catch (SQLException ignored)
        {
            ignored.printStackTrace();
        }
    }

    private void sendPrivateMessage(Xeus avaire,User user, String content, Carbon timeStamp, int id)
    {
        user.openPrivateChannel().queue(message -> message.sendMessage(
            buildMessage(user,content,timeStamp)).queue(success ->
            {
                markMessageSent(avaire, id);
            }
        ));
    }

    private void markMessageSent(Xeus avaire,int id)
    {
        try
        {
            avaire.getDatabase().newQueryBuilder(Constants.REMINDERS_TABLE_NAME)
                .useAsync(true)
                .where("id",id)
                .update(statement -> statement.set("sent",true));
        }
        catch (SQLException ignored)
        {
            ignored.printStackTrace();
        }

    }

    private Message buildMessage(User author, String content, Carbon timeStamp)
    {
        return new MessageBuilder()
            .setContent(String.format("%s, %s you asked to be reminded about:",
                author.getAsMention(),
                timeStamp.diffForHumans()
            ))
            .setEmbeds(new EmbedBuilder()
                .setAuthor(author.getName(), null, author.getEffectiveAvatarUrl())
                .setColor(new Color(43, 255, 0))
                .setDescription(content)
                .build()
            ).build();
    }
}
