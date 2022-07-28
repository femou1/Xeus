package com.pinewoodbuilders.roblox.verification.methods;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.contracts.verification.VerificationResult;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.awt.*;
import java.time.Instant;

public class VerificationMethodsManager {
    private final Xeus avaire;
    private final RobloxAPIManager robloxAPIManger;

    public VerificationMethodsManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.robloxAPIManger = robloxAPIManager;
    }

    public void slashCommandVerify(Member member, Guild guild, InteractionHook hook) {

        GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guild);
        VerificationResult result = robloxAPIManger.getVerification().verify(transformer, member, guild, true);

        String image = getImageFromVerificationEntity(result.getVerificationEntity());
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor(member.getUser().getName(), "https://xeus.pinewood-builders.com", member.getUser().getEffectiveAvatarUrl())
            .setDescription(result.getMessage())
            .setTimestamp(Instant.now())
            .setThumbnail(image != null ? image : "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=1")
            .setFooter(result.getVerificationEntity() != null ? result.getVerificationEntity().getRobloxUsername() : "Verification failed...");

        if (!result.isSuccess()) {
            eb.setColor(new Color(255, 0, 0));
            hook.setEphemeral(true).sendMessage(member.getAsMention()).addEmbeds(eb.build()).queue();
        } else {
            eb.setColor(new Color(0, 255, 0));
            hook.sendMessage(member.getAsMention()).addEmbeds(eb.build()).queue();
        }
    }

    private String getImageFromVerificationEntity(VerificationEntity ve) {
        if (ve == null) {
            return null;
        }
        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }
}
