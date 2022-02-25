package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;

import java.util.ArrayList;

public class VoiceChannelHandler extends EventAdapter {

    public ArrayList <String> voiceChannels = new ArrayList <>();

    public VoiceChannelHandler(Xeus avaire) {
        super(avaire);
    }

    long owner = Permission.ALL_CHANNEL_PERMISSIONS;


    public void createPrivateChannelOnLobbyJoin(GuildVoiceJoinEvent event) {
        createPrivateChannel(event.getGuild(), event.getChannelJoined(), event.getMember());
    }

    public void createPrivateChannelOnLobbyJoinFromMove(GuildVoiceMoveEvent event) {
        if (voiceChannels.contains(event.getChannelLeft().getId())) {
            if (event.getChannelLeft().getMembers().size() < 1) {removeWhenEmpty(event.getChannelLeft());}
        }
        createPrivateChannel(event.getGuild(), event.getChannelJoined(), event.getMember());
    }


    private void createPrivateChannel(Guild guild, VoiceChannel channelJoined, Member member) {
        GuildTransformer transformer = GuildController.fetchGuild(avaire, guild);
        if (transformer == null) return;
        if (transformer.getAutoChannel() == null) return;
        if (!transformer.getAutoChannel().equals(channelJoined.getId())) return;
        if (channelJoined.getParent() == null) return;
        channelJoined.getParent()
            .createVoiceChannel(channelJoined.getName() + " | " + member.getEffectiveName())
            .addMemberPermissionOverride(member.getIdLong(), owner, 0)
            .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), owner, 0)
            .setPosition(channelJoined.getPosition() - 1).queue(
            success -> {
                voiceChannels.add(success.getId());
                guild.moveVoiceMember(member, success).queue();
            },
            failure -> guild.kickVoiceMember(member).queue()
        );
    }

    public void removePrivateChannelOn(GuildVoiceLeaveEvent event) {
        if (!voiceChannels.contains(event.getChannelLeft().getId())) return;
        VoiceChannel vc = event.getChannelLeft();
        if (vc.getMembers().size() < 1) {
            removeWhenEmpty(vc);
        }
    }

    private void removeWhenEmpty(VoiceChannel vc) {
        voiceChannels.remove(vc.getId());
        vc.delete().queue();
    }

    public void onVoiceDelete(VoiceChannelDeleteEvent event) {
        if (!voiceChannels.contains(event.getChannel().getId())) return;
        voiceChannels.remove(event.getChannel().getId());
    }
}
