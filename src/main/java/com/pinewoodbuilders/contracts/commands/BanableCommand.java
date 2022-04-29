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

package com.pinewoodbuilders.contracts.commands;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BanableCommand extends Command {

    private final Pattern timeRegEx = Pattern.compile("([0-9]+[w|d|h|m|s])");

    /**
     * Creates the given command instance by calling
     * {@link Command#Command(Xeus, boolean)} with allowDM set to true.
     *
     * @param avaire The Xeus class instance.
     */
    public BanableCommand(Xeus avaire) {
        super(avaire);
    }

    /**
     * Creates the given command instance with the given Xeus instance and the
     * allowDM settings.
     *
     * @param avaire  The Xeus class instance.
     * @param allowDM Determines if the command can be used in DMs.
     */
    public BanableCommand(Xeus avaire, boolean allowDM) {
        super(avaire, allowDM);
    }

    /**
     * Bans the mentioned user from the current server is a valid user was given.
     *
     * @param command The command that was used in banning the user.
     * @param context The message context object for the current message.
     * @param args    The arguments given by the user who ran the command.
     * @param soft    Determines if the user should be softbanned or not.
     * @return True if the user was banned successfully, false otherwise.
     */
    protected boolean ban(Xeus avaire, Command command, CommandMessage context, String[] args, boolean soft) {
        User user = MentionableUtil.getUser(context, args);
        if (user != null) {
            return banMemberOfServer(avaire, command, context, user, args, soft);
        }

        if (args.length > 0 && NumberUtil.isNumeric(args[0]) && args[0].length() > 16) {
            try {
                long userId = Long.parseLong(args[0], 10);

                Member member = context.getGuild().getMemberById(userId);
                if (member != null) {
                    return banMemberOfServer(avaire, command, context, member.getUser(), args, soft);
                }

                return banUserById(avaire, command, context, userId, args, soft);
            } catch (NumberFormatException ignored) {
                // This should never really be called since we check if
                // the argument is a number in the if-statement above.
            }
        }
        return command.sendErrorMessage(context, context.i18n("mustMentionUser"));
    }

    private boolean banUserById(Xeus avaire, Command command, CommandMessage context, long userId, String[] args,
            boolean soft) {
        Carbon expiresAt = null;
        if (args.length > 1) {
            expiresAt = parseTime(args[1]);
        }

        if (expiresAt != null && expiresAt.copy().subSeconds(61).isPast()) {
            return sendErrorMessage(context, context.i18n("invalidTimeGiven"));
        }

        String reason = generateReason(Arrays.copyOfRange(args, expiresAt == null ? 1 : 2, args.length));
        ModlogType type = expiresAt == null ? ModlogType.BAN : ModlogType.TEMP_BAN;

        final Carbon finalExpiresAt = expiresAt;

        context.getGuild().ban(UserSnowflake.fromId(userId), soft ? 0 : 7, String.format("%s - %s#%s (%s)", reason,
                context.getAuthor().getName(), context.getAuthor().getDiscriminator(), context.getAuthor().getId()))
                .queue(aVoid -> {
                    User user = avaire.getShardManager().getUserById(userId);
                    String caseId = null;
 
                    if (user != null) {
                        caseId = Modlog.log(avaire, context,
                                new ModlogAction(type, context.getAuthor(), user,
                                        finalExpiresAt != null
                                                ? finalExpiresAt.toDayDateTimeString() + " ("
                                                        + finalExpiresAt.diffForHumans(true) + ")" + "\n" + reason
                                                : "\n" + reason));
                    } else {
                        ModlogAction action = new ModlogAction(type, context.getAuthor(), userId,
                                        finalExpiresAt != null
                                                ? finalExpiresAt.toDayDateTimeString() + " ("
                                                        + finalExpiresAt.diffForHumans(true) + ")" + "\n" + reason
                                                : "\n" + reason);
                        caseId = Modlog.log(avaire, context,action);
                    }
                    try {
                        if (finalExpiresAt != null) avaire.getBanManger().registerBan(caseId, context.getGuild().getIdLong(), userId, finalExpiresAt);
                
                    context.makeSuccess(":target has been banned :time")
                        .set("target", user != null ? user.getAsMention() : userId)
                        .set("time", finalExpiresAt == null
                            ? "permenantly"
                            : String.format("for %s", finalExpiresAt.diffForHumans(true)))
                        .queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }, throwable -> context.makeWarning(context.i18n("failedToBan")).set("target", userId)
                        .set("error", throwable.getMessage()).queue());

        return true;
    }

    private boolean banMemberOfServer(Xeus avaire, Command command, CommandMessage context, User user, String[] args,
            boolean soft) {
        if (userHasHigherRole(user, context.getMember())) {
            return command.sendErrorMessage(context, context.i18n("higherRole"));
        }

        if (!context.getGuild().getSelfMember().canInteract(context.getGuild().getMember(user))) {
            return sendErrorMessage(context, context.i18n("userHaveHigherRole", user.getAsMention()));
        }

        Carbon expiresAt = null;
        if (args.length > 1) {
            expiresAt = parseTime(args[1]);
        }

        if (expiresAt != null && expiresAt.copy().subSeconds(61).isPast()) {
            return sendErrorMessage(context, context.i18n("invalidTimeGiven"));
        }

        String reason = generateReason(Arrays.copyOfRange(args, expiresAt == null ? 1 : 2, args.length));
        ModlogType type = expiresAt == null ? ModlogType.BAN : ModlogType.TEMP_BAN;

        final Carbon finalExpiresAt = expiresAt;

        ModlogAction modlogAction = new ModlogAction(type, context.getAuthor(), user,
            finalExpiresAt != null
                ? finalExpiresAt.toDayDateTimeString() + " (" + finalExpiresAt.diffForHumans(true) + ")" + "\n"
                + reason
                : "\n" + reason);

        String caseId = Modlog.log(avaire, context, modlogAction);

        Modlog.notifyUser(user, context.getGuild(), modlogAction, caseId);

        context.getGuild().ban(user, soft ? 0 : 7, String.format("%s - %s#%s (%s)", reason,
                context.getAuthor().getName(), context.getAuthor().getDiscriminator(), context.getAuthor().getId()))
            .queue(aVoid -> {
                try {
                    if (finalExpiresAt != null) avaire.getBanManger().registerBan(caseId, context.getGuild().getIdLong(), user.getIdLong(), finalExpiresAt);


                    context.makeSuccess(":target has been banned :time")
                        .set("target", user.getAsMention())
                        .set("time", finalExpiresAt == null
                            ? "permenantly"
                            : String.format("for %s", finalExpiresAt.diffForHumans(true)))
                        .queue();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }, throwable -> context.makeWarning(context.i18n("failedToBan"))
                .set("target", user.getName() + "#" + user.getDiscriminator())
                .set("error", throwable.getMessage()).queue());
        return true;
    }

    private boolean userHasHigherRole(User user, Member author) {
        Role role = RoleUtil.getHighestFrom(author.getGuild().getMember(user));
        return role != null && RoleUtil.isRoleHierarchyHigher(author.getRoles(), role);
    }

    private String generateReason(String[] args) {
        return args.length == 0 ?
            "No reason was given." :
            String.join(" ", args);
    }

    private Carbon parseTime(String string) {
        Matcher matcher = timeRegEx.matcher(string);
        if (!matcher.find()) {
            return null;
        }

        Carbon time = Carbon.now().addSecond();
        do {
            String group = matcher.group();

            String type = group.substring(group.length() - 1, group.length());
            int timeToAdd = NumberUtil.parseInt(group.substring(0, group.length() - 1));

            switch (type.toLowerCase()) {
                case "w":
                    time.addWeeks(timeToAdd);
                    break;

                case "d":
                    time.addDays(timeToAdd);
                    break;

                case "h":
                    time.addHours(timeToAdd);
                    break;

                case "m":
                    time.addMinutes(timeToAdd);
                    break;

                case "s":
                    time.addSeconds(timeToAdd);
                    break;
            }
        } while (matcher.find());

        return time;
    }
}
