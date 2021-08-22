package com.avairebot.handlers.adapter;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.contracts.verification.VerificationEntity;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.collection.DataRow;
import com.avairebot.database.controllers.GuildController;
import com.avairebot.database.transformers.GuildTransformer;
import com.avairebot.factories.MessageFactory;
import com.avairebot.requests.service.user.rank.RobloxUserGroupRankService;
import com.avairebot.utilities.CheckPermissionUtil;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class SlashCommandEventAdapter {
    private final AvaIre avaire;

    public SlashCommandEventAdapter(AvaIre avaire) {
        this.avaire = avaire;
    }


    public boolean runSlashCommandCheck(SlashCommandEvent event) {
        switch (event.getName()) {
            case "verify":
                return runMemberVerify(event);
            case "update":
                return runMemberUpdate(event);
            case "whois":
                return returnWhoisCommand(event);
            default:
                event.deferReply().queue(l -> {
                    l.setEphemeral(true).sendMessage("Slash command does not exist").queue();
                });
                return false;
        }
    }

    private boolean returnWhoisCommand(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue(l -> {
            VerificationEntity verifiedRobloxUser;
            if (event.getOption("member") != null) {
                verifiedRobloxUser = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(event.getOption("member").getAsUser().getId());
            } else {
                verifiedRobloxUser = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(event.getMember().getId());
            }

            if (verifiedRobloxUser == null) {
                l.sendMessage("No account found on any API. Please verify yourself by running `!verify`").queue();
                return;
            }

            try {
                Collection qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME).orderBy("roblox_group_id").get();
                StringBuilder sb = new StringBuilder();

                for (DataRow data : qb) {
                    if (data.getString("roblox_group_id") != null) {
                        List <RobloxUserGroupRankService.Data> ranks = avaire.getRobloxAPIManager().getUserAPI().getUserRanks(verifiedRobloxUser.getRobloxId());
                        for (RobloxUserGroupRankService.Data rank : ranks) {
                            if (rank.getGroup().getId() == data.getLong("roblox_group_id")) {
                                if (rank.getRole().getRank() >= data.getInt("minimum_hr_rank")) {
                                    sb.append("\n**").append(data.getString("name")).append("** - `").append(rank.getRole().getName()).append("` (`").append(rank.getRole().getRank()).append("`)");
                                } else {
                                    sb.append("\n").append(data.getString("name")).append(" - `").append(rank.getRole().getName()).append("` (`").append(rank.getRole().getRank()).append("`)");
                                }

                            }
                        }
                    }
                }

                l.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(event.getChannel(), new Color(0, 255, 0),
                    "**Roblox Username**: :rusername\n" +
                        "**Roblox ID**: :userId\n" +
                        "**Ranks**:\n" +
                        ":userRanks")
                    .set("rusername", verifiedRobloxUser.getRobloxUsername())
                    .set("userId", verifiedRobloxUser.getRobloxId())
                    .set("userRanks", sb.toString())

                    .setThumbnail(getImageFromVerificationEntity(verifiedRobloxUser)).buildEmbed()).queue();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });
        return true;
    }

    private String getImageFromVerificationEntity(VerificationEntity ve) {
        if (ve == null) {
            return null;
        }
        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }

    private boolean runMemberUpdate(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue(l -> {
            if (!event.isFromGuild()) {
                l.sendMessage("Run this command in a guild.").queue();
                return;
            }

            GuildTransformer guildTransformer = GuildController.fetchGuild(avaire, event.getGuild());
            if (guildTransformer == null) {
                l.sendMessage("GuildTransformer is empty.").queue();
                return;
            }
            if (CheckPermissionUtil.getPermissionLevel(guildTransformer, event.getGuild(), event.getOption("member").getAsMember()).getLevel() < CheckPermissionUtil.GuildPermissionCheckType.MOD.getLevel())
                avaire.getRobloxAPIManager().getVerification().getVerificationMethodsManager().slashCommandVerify(event.getOption("member").getAsMember(), event.getGuild(), l);
        });
        return false;
    }

    private boolean runMemberVerify(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue(l -> avaire.getRobloxAPIManager()
            .getVerification()
            .getVerificationMethodsManager()
            .slashCommandVerify(event.getMember(), event.getGuild(), l));
        return false;
    }


}
