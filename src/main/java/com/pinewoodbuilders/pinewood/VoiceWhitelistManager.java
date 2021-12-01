package com.pinewoodbuilders.pinewood;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.HashMap;

public class VoiceWhitelistManager {

    private final Xeus avaire;
    private final HashMap<AudioChannel, ArrayList<Member>> whitelists = new HashMap<>();

    public VoiceWhitelistManager(Xeus avaire) {
        this.avaire = avaire;
    }

    public HashMap<AudioChannel, ArrayList<Member>> getWhitelists() {
        return whitelists;
    }

    public ArrayList<Member> getWhitelist(AudioChannel vc) {
        return whitelists.getOrDefault(vc, null);
    }

    public boolean addUserToWhitelist(CommandMessage context, Member whitelisted) {

        if (whitelisted.getUser().isBot()) {
            context.makeError("Sorry, but you're not allowed to add bots to the whitelist.").queue();
            return false;
        }

        Member whitelister = context.member;
        GuildVoiceState voiceState = whitelister.getVoiceState();
        if (voiceState == null) {
            context.makeError("VoiceState does not exist").queue();
            return false;
        }

        if (!voiceState.inAudioChannel()) {
            context.makeError("Sorry, but :whitelister is not in a (valid) voice channel.")
                .set("whitelister", whitelister.getEffectiveName()).queue();
            return false;
        }


        AudioChannel vc = voiceState.getChannel();
        if (getWhitelist(vc) == null) {
            context.makeError("Your voice channel ``:vc`` does not have a whitelist enabled.").set("vc", vc.getName()).queue();
            return false;
        }

        if (getWhitelist(voiceState.getChannel()).contains(whitelisted)) {
            context.makeWarning(":member is already whitelisted!")
                .set("member", whitelisted.getEffectiveName()).queue();
            return true;
        }

        getWhitelist(vc).add(whitelisted);
        context.makeSuccess("Added :whitelisted to the whitelist.").set("whitelisted", whitelisted.getEffectiveName()).queue();
        return true;
    }
    public boolean removeUserFromWhitelist(CommandMessage context, Member whitelisted) {
        if (whitelisted.getUser().isBot()) {
            context.makeError("Sorry, but you're not allowed to remove bots from the whitelist.").queue();
            return false;
        }

        Member whitelister = context.member;
        GuildVoiceState voiceState = whitelister.getVoiceState();
        if (voiceState == null) {
            context.makeError("VoiceState does not exist").queue();
            return false;
        }

        if (!voiceState.inAudioChannel()) {
            context.makeError("Sorry, but :whitelister is not in a (valid) voice channel.")
                .set("whitelister", whitelister.getEffectiveName()).queue();
            return false;
        }


        AudioChannel vc = voiceState.getChannel();
        if (getWhitelist(vc) == null) {
            context.makeError("Your voice channel ``:vc`` does not have a whitelist enabled.").set("vc", vc.getName()).queue();
            return false;
        }

        if (!getWhitelist(voiceState.getChannel()).contains(whitelisted)) {
            context.makeWarning(":member is not whitelisted!")
                .set("member", whitelisted.getEffectiveName()).queue();
            return true;
        }

        getWhitelist(vc).remove(whitelisted);
        context.makeSuccess("Removed :whitelisted from the whitelist.").set("whitelisted", whitelisted.getEffectiveName()).queue();
        return true;
    }

    public boolean addAllUsersInVcToWhitelistAndEnable(CommandMessage context) {
        Member whitelister = context.member;
        GuildVoiceState voiceState = whitelister.getVoiceState();
        if (voiceState == null) {
            context.makeError("VoiceState does not exist").queue();
            return false;
        }

        if (!voiceState.inAudioChannel()) {
            context.makeError("Sorry, but ``:whitelister`` (You) are not in a (valid) voice channel.")
                .set("whitelister", whitelister.getEffectiveName()).queue();
            return false;
        }


        AudioChannel vc = voiceState.getChannel();
        if (getWhitelist(vc) == null) {
            context.makeError("Your voice channel ``:vc`` does not have a whitelist enabled. However, it will now be enabled.").set("vc", vc.getName()).queue();
            addVoiceChannelToWhitelists(vc);
        }


        for (Member m : vc.getMembers()) {
            if (!isInWhitelist(vc, m)) {
                getWhitelist(vc).add(m);
                context.makeSuccess(m.getAsMention() + " has been added to the " + vc.getName() + " whitelist!").queue();
            } else {
                context.makeWarning(m.getAsMention() + " was already added to the " + vc.getName() + " whitelist!").queue();
            }
        }
        return false;
    }

    public boolean isInWhitelist(AudioChannel vc, Member joiner) {
        ArrayList<Member> whitelist = getWhitelist(vc);
        if (whitelist == null) {
            return false;
        }

        return whitelist.contains(joiner);
    }

    public boolean hasWhitelist(AudioChannel vc) {
        return whitelists.containsKey(vc);
    }

    public boolean addVoiceChannelToWhitelists(AudioChannel vc) {
        whitelists.put(vc, new ArrayList<>());
        return true;
    }

    public boolean removeVoiceChannelFromWhitelists(AudioChannel vc) {
        whitelists.remove(vc);
        return true;
    }
}
