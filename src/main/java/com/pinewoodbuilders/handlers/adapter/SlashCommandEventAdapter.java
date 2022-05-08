package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SlashCommandEventAdapter extends EventAdapter {

    private final static String LINESTART = " ▶ " + " ";
    private final static String ROLE_EMOJI = "\uD83C\uDFAD"; // 🎭


    public SlashCommandEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public boolean runUserInteractionEvent(UserContextInteractionEvent event) {
        switch (event.getName()) {
            case "whois":
                return returnWhoisCommand(event);
            default:
                event.deferReply().queue(l -> {
                    l.setEphemeral(true).sendMessage("Slash command does not exist").queue();
                });
                return false;
        }
    }

    public boolean runSlashCommandCheck(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "verify":
                return runMemberVerify(event);
            case "update":
                return runMemberUpdate(event);
            case "whois":
                return returnWhoisCommand(event);
            case "roleinfo":
                return roleInfoCommand(event);
            default:
                event.deferReply().queue(l -> {
                    l.setEphemeral(true).sendMessage("Slash command does not exist").queue();
                });
                return false;
        }
    }

    public static String escapeMentions(@NotNull String string) {
        return string.replace("@everyone", "@\u0435veryone")
            .replace("@here", "@h\u0435re")
            .replace("discord.gg/", "dis\u0441ord.gg/");
    }
    private boolean roleInfoCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue(
            l -> {
                Role role = event.getOption("role").getAsRole();
                final String title = (ROLE_EMOJI + " Role " + escapeMentions(role.getName()));
                Color color = role.getColor();

                StringBuilder description = new StringBuilder(""
                + LINESTART + "ID: **" + role.getId() + "**\n"
                + LINESTART + "Creation: **" + role.getTimeCreated().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "**\n"
                + LINESTART + "Position: **" + role.getPosition() + "**\n"
                + LINESTART + "Color: **#" + (color == null ? "000000" : Integer.toHexString(color.getRGB()).toUpperCase().substring(2)) + "**\n"
                + LINESTART + "Mentionable: **" + (role.isMentionable() ? "✅" : "❌") + "**\n"
                + LINESTART + "Hoisted: **" + (role.isHoisted()? "✅" : "❌") + "**\n"
                + LINESTART + "Managed: **" + (role.isManaged() ? "✅" : "❌") + "**\n"
                + LINESTART + "Public Role: **" + (role.isPublicRole() ) + "**\n"
                + LINESTART + "Members: **" + getMembersWithRole(role, event.getGuild()) + "**\n"
                + LINESTART + "Permissions: \n");
            
                if (role.getPermissions().isEmpty()) {
                    description.append("No permissions set");
                } else {
                    description.append(role.getPermissions().stream().map(p -> "`, `" + p.getName()).reduce("", String::concat)
                        .substring(3)).append("`");
                }

                l.sendMessageEmbeds(new EmbedBuilder().setDescription(description).setColor(color).setTitle(title).build()).queue();
            }
        );
        return false;
    }
    private String getMembersWithRole(Role role, Guild guild) {
        int membersWithRole = 0;
        for (Member m : guild.getMembers()) {
            if (m.getRoles().contains(role)) {
                membersWithRole++;
            }
        }

        if (membersWithRole > 0) {
            return String.valueOf(membersWithRole);
        }
        return "No members found";
    }

    private boolean returnWhoisCommand(CommandInteraction event) {
        event.deferReply().setEphemeral(true).queue(l -> {
            Member m = event.getOption("member") != null ? event.getOption("member").getAsMember() : null;
            if (event instanceof UserContextInteractionEvent user) {
                m = user.getTargetMember();
            }

            VerificationEntity verifiedRobloxUser;
            if (m != null) {
                verifiedRobloxUser = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(m.getId());
            } else {
                verifiedRobloxUser = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(event.getMember().getId());
            }

            if (verifiedRobloxUser == null) {
                l.sendMessage("No account found on any API. Please verify yourself by running `!verify`").queue();
                return;
            }

            try {
                Collection qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).orderBy("roblox_group_id").get();
                StringBuilder sb = new StringBuilder();

                for (DataRow data : qb) {
                    Guild g = event.getJDA().getGuildById(data.getString("id"));
                    if (g == null) continue;
                    if (sb.toString().contains(String.valueOf(data.getInt("roblox_group_id")))) continue;
                    if (!data.getBoolean("official_sub_group")) continue;

                    if (data.getString("roblox_group_id") != null) {
                        List<RobloxUserGroupRankService.Data> ranks = avaire.getRobloxAPIManager().getUserAPI().getUserRanks(verifiedRobloxUser.getRobloxId());
                        for (RobloxUserGroupRankService.Data rank : ranks) {
                            if (rank.getGroup().getId() == data.getLong("roblox_group_id")) {
                                if (rank.getRole().getRank() >= data.getInt("minimum_hr_rank")) {
                                    sb.append("\n**").append(g.getName()).append("** - `").append(rank.getRole().getName()).append("` (`").append(rank.getRole().getRank()).append("`)");
                                } else {
                                    sb.append("\n").append(g.getName()).append(" - `").append(rank.getRole().getName()).append("` (`").append(rank.getRole().getRank()).append("`)");
                                }
    
                            }
                        }
                    }
                }

                l.sendMessageEmbeds(MessageFactory.makeEmbeddedMessage(event.getMessageChannel(), new Color(0, 255, 0),
                        """
                            **Roblox Username**: :rusername
                            **Roblox ID**: :userId
                            **Ranks**:
                            :userRanksn

                            **Linked Discord Account**:
                            :discordAcc""")
                    .set("rusername", verifiedRobloxUser.getRobloxUsername())
                    .set("userId", verifiedRobloxUser.getRobloxId())
                    .set("userRanks", sb.toString())
                    .set("discordAcc", "<@" + verifiedRobloxUser.getDiscordId() + ">")
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

    private boolean runMemberUpdate(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue(l -> {
            Guild g = event.getGuild();
            if (!event.isFromGuild() || g == null) {
                l.sendMessage("Run this command in a guild.").queue();
                return;
            }

            avaire.getRobloxAPIManager().getVerification().getVerificationMethodsManager().slashCommandVerify(event.getOption("member").getAsMember(), g, l);
        });
        return false;
    }

    private boolean runMemberVerify(SlashCommandInteractionEvent event) {
        event.deferReply().queue(l -> avaire.getRobloxAPIManager()
            .getVerification()
            .getVerificationMethodsManager()
            .slashCommandVerify(event.getMember(), event.getGuild(), l));
        return false;
    }


}
