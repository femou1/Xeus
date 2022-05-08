/*
 * Copyright (c) 2018.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.commands.administration;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.requests.service.user.rank.RobloxUserGroupRankService;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UserInfoCommand extends Command {

    public UserInfoCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "User Info Command";
    }

    @Override
    public String getDescription() {
        return "Shows information about the user that ran the command, or the mentioned user. This includes the users username, ID, roles, the date they joined the server, the date they created their account, and how many servers they're in (That Ava knows about).";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command [user]` - Gets information about the user who ran the command, or the mentioned user");
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command @Senither`",
            "`:command alexis`",
            "`:command 88739639380172800`"
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Collections.singletonList(UserIdCommand.class);
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("userinfo", "uinfo", "whois");
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.INFORMATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        Member member = context.getMember();
        if (args.length > 0) {
            User user = MentionableUtil.getUser(context, new String[]{String.join(" ", args)});
            if (user == null) {
                return sendErrorMessage(context, "errors.noUsersWithNameOrId", args[0]);
            }
            member = context.getGuild().getMember(user);
        }

        if (member == null) {
            return sendErrorMessage(context, "errors.noUsersWithNameOrId", args[0]);
        }

        Carbon joinedDate = Carbon.createFromOffsetDateTime(member.getTimeJoined());
        Carbon createdDate = Carbon.createFromOffsetDateTime(member.getUser().getTimeCreated());

        PlaceholderMessage placeholderMessage = context.makeEmbeddedMessage(getRoleColor(member.getRoles()),
            new MessageEmbed.Field(
                context.i18n("fields.username"),
                member.getUser().getName(),
                true
            ),
            new MessageEmbed.Field(
                context.i18n("fields.userId"),
                member.getUser().getId(),
                true
            ),
            new MessageEmbed.Field(
                context.i18n("fields.joinedServer"),
                joinedDate.format(context.i18n("timeFormat")) + "\n*About " + shortenDiffForHumans(joinedDate) + "*",
                true
            ),
            new MessageEmbed.Field(
                context.i18n("fields.joinedDiscord"),
                createdDate.format(context.i18n("timeFormat")) + "\n*About " + shortenDiffForHumans(createdDate) + "*",
                true
            )
        ).setThumbnail(member.getUser().getEffectiveAvatarUrl());

        String memberRoles = context.i18n("noRoles");
        if (!member.getRoles().isEmpty()) {
            memberRoles = member.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.joining("\n"));
        }

        placeholderMessage.addField(new MessageEmbed.Field(
            context.i18n("fields.roles", member.getRoles().size()), memberRoles, true
        ));

        placeholderMessage.addField(new MessageEmbed.Field(
            context.i18n("fields.servers"),
            context.i18n("inServers", NumberUtil.formatNicely(
                avaire.getShardManager().getMutualGuilds(member.getUser()).size()
            )), true));

        placeholderMessage.requestedBy(context.getMember());
        Member finalMember = member;
        context.getMessageChannel().sendMessageEmbeds(placeholderMessage.buildEmbed())
            .flatMap(message -> message.editMessageEmbeds(placeholderMessage.buildEmbed(), buildVerificationEmbed(context, finalMember)))
            .queue();
        return true;
    }

    private MessageEmbed buildVerificationEmbed(CommandMessage context, Member member) {
        VerificationEntity verifiedRobloxUser;
        if (member != null) {
            verifiedRobloxUser = avaire.getRobloxAPIManager().getVerification().fetchVerification(member.getId(), true);
        } else {
            verifiedRobloxUser = avaire.getRobloxAPIManager().getVerification().fetchVerification(context.getMember().getId(), true);
        }

        if (verifiedRobloxUser == null) {
            return new EmbedBuilder().setDescription("No account found on any API. Please verify yourself by running `!verify`").build();
        }

        try {
            Collection qb = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE).orderBy("roblox_group_id").get();
            StringBuilder sb = new StringBuilder();

            for (DataRow data : qb) {
                Guild g = context.getJDA().getGuildById(data.getString("id"));
                if (g == null) continue;
                if (sb.toString().contains(String.valueOf(data.getInt("roblox_group_id")))) continue;
                if (data.getBoolean("leadership_server")) {continue;}
                if (data.getString("roblox_group_id") != null) {
                    List<RobloxUserGroupRankService.Data> ranks = avaire.getRobloxAPIManager().getUserAPI().getUserRanks(verifiedRobloxUser.getRobloxId());
                    if (ranks != null) {
                        for (RobloxUserGroupRankService.Data rank : ranks) {
                            if (rank.getGroup().getId() == data.getLong("roblox_group_id")) {
                                if (rank.getRole().getRank() >= data.getInt("minimum_hr_rank")) {
                                    sb.append("\n**").append(g.getName()).append("** - `").append(rank.getRole().getName()).append("` (`").append(rank.getRole().getRank()).append("`)");
                                } else {
                                    sb.append("\n").append(g.getName()).append(" - `").append(rank.getRole().getName()).append("` (`").append(rank.getRole().getRank()).append("`)");
                                }

                            }
                        }
                    } else {
                        sb.append("***No roles found for `").append(g.getName()).append("`***");
                    }

                }
            }

            return context.makeInfo(
                    """
                        **Roblox Username**: :rusername
                        **Roblox ID**: :userId
                        **Ranks**:
                        :userRanks""")
                .set("rusername", verifiedRobloxUser.getRobloxUsername())
                .set("userId", verifiedRobloxUser.getRobloxId())
                .set("userRanks", sb.toString())
                .setThumbnail(getImageFromVerificationEntity(verifiedRobloxUser))
                .requestedBy(context).buildEmbed();
        } catch (SQLException throwables) {
            return context.makeError(
                "Something went wrong in the database pulling the group ID's, please notify the developer")
                .requestedBy(context).buildEmbed();
        }
    }
    private String getImageFromVerificationEntity(VerificationEntity ve) {
        if (ve == null) {
            return null;
        }
        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }
    private String shortenDiffForHumans(Carbon carbon) {
        String diff = carbon.diffForHumans();
        if (!diff.contains("and")) {
            return diff;
        }
        return diff.split("and")[0] + "ago";
    }

    private Color getRoleColor(List<Role> roles) {
        for (Role role : roles) {
            if (role.getColor() != null) return role.getColor();
        }
        return Color.decode("#E91E63");
    }
}
