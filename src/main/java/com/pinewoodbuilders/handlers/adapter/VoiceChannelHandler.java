package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Xeus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
public class VoiceChannelHandler {
    private final Xeus avaire;
    public VoiceChannelHandler(Xeus avaire) {
        this.avaire = avaire;
    }

    List<VoiceChannel> active = new ArrayList<>();

    public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
        HashMap<VoiceChannel, Guild> autochans = commands.guildAdministration.Autochannel.getAutochans();
        VoiceChannel vc = event.getChannelJoined();
        Guild g = event.getGuild();

        if (autochans.containsKey(vc)) {
            VoiceChannel nvc = (VoiceChannel) g.getController().createVoiceChannel(vc.getName() + " [AC]")
                .setBitrate(vc.getBitrate())
                .setUserlimit(vc.getUserLimit())
                .complete();
            System.out.println(vc.getParent());

            if (vc.getParent() != null)
                nvc.getManager().setParent(vc.getParent()).queue();

            g.getController().modifyVoiceChannelPositions().selectPosition(nvc).moveTo(vc.getPosition() + 1).queue();
            g.getController().modifyVoiceChannelPositions().selectPosition(nvc).moveTo(vc.getPosition() + 1).queue();
            g.getController().moveVoiceMember(event.getMember(), nvc).queue();
            active.add(nvc);
        }
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
        HashMap<VoiceChannel, Guild> autochans = commands.guildAdministration.Autochannel.getAutochans();
        Guild g = event.getGuild();

        VoiceChannel vc = event.getChannelJoined();

        if (autochans.containsKey(vc)) {
            VoiceChannel nvc = (VoiceChannel) g.getController().createVoiceChannel(vc.getName() + " [AC]")
                .setBitrate(vc.getBitrate())
                .setUserlimit(vc.getUserLimit())
                .complete();

            if (vc.getParent() != null)
                nvc.getManager().setParent(vc.getParent()).queue();

            g.getController().modifyVoiceChannelPositions().selectPosition(nvc).moveTo(vc.getPosition() + 1).queue();
            g.getController().moveVoiceMember(event.getMember(), nvc).queue();
            active.add(nvc);
        }

        vc = event.getChannelLeft();

        if (active.contains(vc) && vc.getMembers().size() == 0) {
            active.remove(vc);
            vc.delete().queue();
        }
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
        VoiceChannel vc = event.getChannelLeft();

        if (active.contains(vc) && vc.getMembers().size() == 0) {
            active.remove(vc);
            vc.delete().queue();
        }
    }

    public void onVoiceChannelDelete(VoiceChannelDeleteEvent event) {
        HashMap<VoiceChannel, Guild> autochans = commands.guildAdministration.Autochannel.getAutochans();
        if (autochans.containsKey(event.getChannel())) {
            commands.guildAdministration.Autochannel.unsetChan(event.getChannel());
        }

    }
}
