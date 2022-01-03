package com.pinewoodbuilders.pinewood.adapter;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.pinewood.VoiceWhitelistManager;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;

public class WhitelistEventAdapter {
    private final Xeus avaire;
    private final VoiceWhitelistManager whitelistManager;

    public WhitelistEventAdapter(Xeus avaire, VoiceWhitelistManager whitelist) {
        this.avaire = avaire;
        this.whitelistManager = whitelist;
    }

    public void whitelistCheckEvent(GenericGuildVoiceEvent event) {
        AudioChannel newVc = getNewVC(event);

        if (whitelistManager.hasWhitelist(newVc)) {
            int lvl = XeusPermissionUtil.getPermissionLevel(GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild()), event.getGuild(), event.getMember()).getLevel();
            if (!(lvl >= GuildPermissionCheckType.LOCAL_GROUP_HR.getLevel())) {
                if (!whitelistManager.isInWhitelist(newVc, event.getMember())) {
                    event.getGuild().kickVoiceMember(event.getMember()).queue();
                    event.getMember().getUser().openPrivateChannel().queue(l -> {
                        l.sendMessage("Sorry, but you're not on the whitelist for " + newVc.getName() + ". Please ask the mod who asked you to join to whitelist you. (Unless you passed the window to join)").queue();
                    });

                }
            }
        }


    }

    private AudioChannel getNewVC(GenericGuildVoiceEvent event) {
        if (event instanceof GuildVoiceJoinEvent) {
            return ((GuildVoiceJoinEvent) event).getChannelJoined();
        } else if (event instanceof GuildVoiceMoveEvent) {
            return ((GuildVoiceMoveEvent) event).getChannelJoined();
        } else {
            return event.getVoiceState().getChannel();
        }
    }
}
