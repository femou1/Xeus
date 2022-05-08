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
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.contracts.moderation.WarningGrade;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.moderation.local.warn.WarnContainer;
import com.pinewoodbuilders.modlog.global.moderation.GlobalModlog;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogAction;
import com.pinewoodbuilders.modlog.global.shared.GlobalModlogType;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.modlog.local.watchlog.Watchlog;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.RestActionUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import javax.annotation.Nonnull;
import java.awt.*;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WarnCommand extends Command {

    public WarnCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Warn Command";
    }

    @Override
    public String getDescription() {
        return "Warns a given user with a message, this action will be reported to any channel that has modloging enabled.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <user> [reason]` - Warns the mentioned user with the given reason."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command @Senither Being a potato` - Warns Senither for being a potato."
        );
    }

    @Override
    public List <Class <? extends Command>> getRelations() {
        return Arrays.asList(
            ModlogHistoryCommand.class,
            ModlogReasonCommand.class
        );
    }

    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("warn");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isGuildHROrHigher",
            "throttle:channel,1,5"
        );
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        if (transformer.getModlog() == null) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requiresModlogIsEnabled", prefix));
        }

        if (args.length == 1) {
            return sendErrorMessage(context, "Please tell me why you want to warn this person.");
        }

        User user = MentionableUtil.getUser(context, args);
        if (user == null) {
            return sendErrorMessage(context, context.i18n("mustMentionUse"));
        }

        if (user.isBot()) {
            return sendErrorMessage(context, context.i18n("warnBots"));
        }

        if (user == context.getAuthor()) {
            return sendErrorMessage(context, "Silly person, you can't warn yourself you twat.");
        }


        Carbon expiresAt = null;
        if (args.length > 1) {
            expiresAt = parseTime(args[1].toLowerCase(Locale.ROOT));
        }

        String reason = generateReason(Arrays.copyOfRange(args, expiresAt == null ? 1 : 2, args.length));


        if (expiresAt == null) {
            Carbon expire = Carbon.now().addSecond();
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "t1": expire = expire.addMonths(3);
                case "t2": expire = expire.addMonths(4);
                case "t3": expire = expire.addMonths(5);
                default: expire = expire.addMonths(3);
            }
            expiresAt = expire;
        }

        ModlogAction modlogAction = new ModlogAction(
            ModlogType.WARN,
            context.getAuthor(), user,
            expiresAt != null ? expiresAt.toDayDateTimeString() + " (" + expiresAt.diffForHumans(true) + ")" + "\n" + reason
                : "\n" + reason
        );

        String caseId = Modlog.log(avaire, context.getGuild(), transformer, modlogAction);

        if (caseId == null) {
            return sendErrorMessage(context, context.i18n("failedToLogWarning"));
        }

        if (expiresAt != null) {
            if (modlogAction.getMessage() != null) {
                modlogAction.setMessage(modlogAction.getMessage().replace(expiresAt.toDayDateTimeString() + " (" + expiresAt.diffForHumans(true) + ")", ""));
            }
        }

        Modlog.notifyUser(user, context.getGuild(), modlogAction, caseId);

        context.makeSuccess(context.i18n("message"))
            .set("target", user.getName() + "#" + user.getDiscriminator())
            .set("reason", reason)
            .setFooter("Case ID #" + caseId)
            .setTimestamp(Instant.now())
            .queue(ignoreMessage -> context.delete().queue(null, RestActionUtil.ignore), RestActionUtil.ignore);

        GuildSettingsTransformer settings = context.getGuildSettingsTransformer();
        if (settings == null) return true;
        GlobalSettingsTransformer globalSettings = settings.getGlobalSettings();
        if (globalSettings == null) return true;
        if (!globalSettings.getNewWarnSystem()) return true;

        try {
            avaire.getWarningsManager().registerWarn(caseId, context.guild.getIdLong(), user.getIdLong(), expiresAt);
        } catch (SQLException e) {
            context.makeError("Failed to log the warn in the database, this warn may not expire... Please check with the developer.").queue();
        }


        HashSet <WarnContainer> warns = avaire.getWarningsManager().getWarns(context.getGuild().getIdLong(), user.getIdLong());
        if (warns.size() >= 1) startWarningsCheck(context, user, warns);

        return true;
    }

    private void startWarningsCheck(CommandMessage context, User user, HashSet <WarnContainer> warns) {
        int size = warns.size();
        WarningGrade grade = WarningGrade.getLabelFromWarns(size);

        if (grade == null) return;


        Button globalMute = Button.primary("gmute:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Mute (3 days)");
        Button gwatchmute = Button.primary("gwatchmute:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Mute/Watch");
        Button localBanAndGlobalWatch = Button.secondary("lbanwatch:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Ban and Global Watch");
        Button globalBan = Button.danger("gban:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Ban");
        Button cancel = Button.danger("cancel:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Cancel");

        if (!grade.isGlobalMute()) globalMute = globalMute.asDisabled();
        if (!(grade.isGlobalWatch())) gwatchmute = gwatchmute.asDisabled();
        if (!grade.isLocalBan()) localBanAndGlobalWatch = localBanAndGlobalWatch.asDisabled();
        if (!grade.isGlobalBan()) globalBan = globalBan.asDisabled();

        MessageEmbed messageEmbed = context.makeInfo(user.getAsMention() + " has reached a total of `" + size + "` warns, by this rule. This user should get either of these punishments").buildEmbed();
        ActionRow ar = ActionRow.of(
            globalMute, gwatchmute, localBanAndGlobalWatch, globalBan, cancel
        );
        context.getMessageChannel()
            .sendMessageEmbeds(messageEmbed)
            .setActionRows(ar)
            .queue(message -> listenForInteraction(message, context, user, size, grade));

    }

    private MessageEmbed outputWarnsWithReason(HashSet <WarnContainer> warns) {
        try {
            StringBuilder sb = new StringBuilder();
            int warnNumber = 1;
            for (WarnContainer warn : warns) {
                Collection collection = avaire.getDatabase().newQueryBuilder(Constants.LOG_TABLE_NAME)
                    .where("guild_id", warn.getGuildId())
                    .where("modlogCase", warn.getCaseId())
                    .get();


                if (collection.isEmpty()) {
                    continue;
                }

                DataRow row = collection.first();
                String reason = row.getString("reason");

                sb.append("**").append(warnNumber).append("**").append("\n")
                    .append("```").append(reason).append("```").append("\n").append("\n");
                warnNumber++;
            }
            return new EmbedBuilder().setDescription(sb.toString()).setColor(new Color(1, 1, 1)).build();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new EmbedBuilder().setDescription("Unable to retrieve warns...").setColor(new Color(255, 0, 0)).build();
    }

    private void listenForInteraction(Message messageExecute, CommandMessage context, User user, int size, WarningGrade grade) {
        avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class, buttonClicker -> {
            String[] arguments = buttonClicker.getButton().getId().split(":");
            if (arguments[1].contains(context.getChannel().getId()))
                if (arguments[2].contains(buttonClicker.getUser().getId())) {
                    return messageExecute.equals(buttonClicker.getMessage());
                } else {
                    buttonClicker.getInteraction().deferReply(true).flatMap(mes -> mes.sendMessage("You're not " + context.getMember().getAsMention())).queue();
                }
            return false;
        }, interaction -> {
            interaction.getInteraction().deferEdit()
                .flatMap(k -> messageExecute.editMessageEmbeds(new EmbedBuilder().setDescription("Checking pressed button...").build()).setActionRows(Collections.emptyList()))
                .delay(Duration.ofSeconds(1))
                .queue(edit -> punishUserDependingOnButton(interaction, context, user, edit, size, grade));
        }, 1, TimeUnit.MINUTES, () -> {
            messageExecute.editMessageEmbeds(new EmbedBuilder()
                    .setFooter("Deleting in 30 seconds.").setColor(new Color(255, 0, 0)).setDescription("No response gotten after 1 minute. No punishment is given.").build())
                .delay(Duration.ofSeconds(30))
                .flatMap(Message::delete)
                .queue();
        });
    }

    private void punishUserDependingOnButton(ButtonInteractionEvent interaction, CommandMessage context, User user, Message messageExecute, int size, WarningGrade grade) {
        String[] arguments = interaction.getButton().getId().split(":");
        String punishment = arguments[0];

        switch (punishment) {
            case "gmute" -> muteUserQueue(context, Carbon.now().addSecond().addDays(3), user, messageExecute);
            case "gwatchmute" -> selectWatchOrMuteQueue(context, grade, user, messageExecute, size);
            case "lbanwatch" -> localBanAndGlobalWatch(context, user, messageExecute);
            case "gban" -> globalBan(context, user, messageExecute);
            case "globalmute7" -> muteUserQueue(context, Carbon.now().addWeeks(1).addSecond(), user, messageExecute);
            case "globalwatch7" -> watchUserQueue(context, Carbon.now().addWeeks(1).addSecond(), user, messageExecute);
            case "globalmute14" -> muteUserQueue(context, Carbon.now().addWeeks(2).addSecond(), user, messageExecute);
            case "globalwatch14" -> watchUserQueue(context, Carbon.now().addWeeks(2).addSecond(), user, messageExecute);
            case "cancel" ->
                messageExecute.editMessageEmbeds(new EmbedBuilder().setDescription("No punishments have been given.").build()).setActionRows(Collections.emptyList()).queue();
            default ->
                messageExecute.editMessage("ÍNVALID RESPONSE").setEmbeds(Collections.emptyList()).setActionRow(Collections.emptyList()).queue();
        }
    }

    private void globalBan(CommandMessage context, User user, Message messageExecute) {
        GuildSettingsTransformer settingsTransformer = context.getGuildSettingsTransformer();
        if (settingsTransformer == null) {
            messageExecute.editMessageEmbeds(context.makeError("Guildtransformer is null, please contact the developer.").buildEmbed()).queue();
            return;
        }

        if (settingsTransformer.getMainGroupId() == 0) {
            messageExecute.editMessageEmbeds(context.makeError(
                    "MGI (MainGroupId) has not been set. This command is disabled for that. Ask a GA+ to set the MGI.").buildEmbed())
                .queue();
            return;
        }

        long g = context.getGuildSettingsTransformer().getGlobalSettings().getAppealsDiscordId();

        StringBuilder sb = new StringBuilder();
        String reason = "User has been global-banned due to reaching 15 warnings in " + context.getGuild().getName();

        List <Guild> guilds = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(settingsTransformer.getMainGroupId());

        int bannedGuilds = 0;
        for (Guild guild : guilds) {
            if (g != 0) {
                if (g == guild.getIdLong()) {
                    sb.append("``").append(guild.getName()).append("`` - :x:\n");
                    continue;
                }
            }
            if (guild == null)
                continue;
            if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS))
                continue;

            GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guild);
            if (settings.getGlobalBan()) continue;
            if (settings.isOfficialSubGroup()) {
                guild.ban(UserSnowflake.fromId(user.getId()), 0, "Banned by: " + context.member.getEffectiveName() + "\n" + "For: "
                        + reason
                        + "\n*This is a global-ban ran due to reaching 15 warnings in a guild.*")
                    .reason("Global Ban, executed by " + context.member.getEffectiveName() + ". For: \n"
                        + reason)
                    .queue();
                sb.append("``").append(guild.getName()).append("`` - :white_check_mark:\n");
            } else {
                guild.ban(UserSnowflake.fromId(user.getId()), 0,
                        "This is a global-ban that has been executed from the global ban list of the guild you're subscribed to... ")
                    .queue();
                sb.append("``").append(guild.getName()).append("`` - :ballot_box_with_check:\n");
            }
            bannedGuilds++;
        }

        if (user != null) {
            user.openPrivateChannel().submit().thenAccept(p -> p.sendMessageEmbeds(context.makeInfo(
                    "*You have been **global-banned** from all discord that are connected to [this group](:groupLink) by an MGM Moderator. "
                        + "For the reason: *```" + reason + "```\n\n"
                        + "If you feel that your ban was unjustified please appeal at the group in question;")
                .setColor(Color.BLACK)
                .set("groupLink",
                    "https://roblox.com/groups/"
                        + context.getGuildSettingsTransformer().getGlobalSettings().getMainGroupId())
                .buildEmbed()).submit()).whenComplete((message, error) -> {
                if (error != null) {
                    error.printStackTrace();
                }
            });
        }

        long mgmLogs = context.getGuildSettingsTransformer().getGlobalSettings().getMgmLogsId();
        if (mgmLogs != 0) {
            TextChannel tc = avaire.getShardManager().getTextChannelById(mgmLogs);
            if (tc != null) {
                tc.sendMessageEmbeds(context.makeInfo(
                        "[``:global-unbanned-id`` was global-banned from all discords that have global-ban enabled. Banned by ***:user*** in `:guild` for](:link):\n"
                            + "```:reason```")
                    .set("global-unbanned-id", user.getId()).set("reason", reason)
                    .set("guild", context.getGuild().getName())
                    .set("user", context.getMember().getAsMention())
                    .set("link", context.getMessage().getJumpUrl()).buildEmbed()).queue();
            }
        }

        messageExecute.editMessage("<@&788316320747094046>").setEmbeds(context.makeSuccess(
                "<@" + user.getId() + "> (" + user.getId() + ") has been banned from `:guilds` guilds : \n\n" + sb)
            .set("guilds", bannedGuilds).buildEmbed()).queue();

        VerificationEntity ve = avaire.getRobloxAPIManager().getVerification()
            .fetchInstantVerificationWithBackup(user.getId());
        try {
            handleGlobalPermBan(context, user.getId(), reason, ve);
        } catch (SQLException exception) {
            Xeus.getLogger().error("ERROR: ", exception);
            context.makeError("Something went wrong adding this user to the global perm ban database.").queue();
        }
    }

    private void handleGlobalPermBan(CommandMessage context, String args, String reason, VerificationEntity ve)
        throws SQLException {
        Collection c = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("userId", args)
            .where("main_group_id", context.getGuildSettingsTransformer().getMainGroupId()).get();
        if (c.size() < 1) {
            /*avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).insert(o -> {
                o.set("userId", args[0]).set("punisherId", context.getAuthor().getId()).set("reason", reason, true)
                    .set("roblox_user_id", getRobloxIdFromVerificationEntity(ve))
                    .set("main_group_id", context.getGuildSettingsTransformer().getMainGroupId());
            });*/
            if (ve == null) {
                avaire.getGlobalPunishmentManager().registerGlobalBan(context.getAuthor().getId(),
                    context.getGuildSettingsTransformer().getMainGroupId(),
                    args, 0, null, reason);

            } else {
                avaire.getGlobalPunishmentManager().registerGlobalBan(context.getAuthor().getId(),
                    context.getGuildSettingsTransformer().getMainGroupId(),
                    args, ve.getRobloxId(), ve.getRobloxUsername(), reason);
            }

            context.makeSuccess("Permbanned ``" + args + "`` in the database.").queue();
        } else {
            context.makeError("This user already has a permban in the database!").queue();
        }
    }

    private void localBanAndGlobalWatch(CommandMessage context, User user, Message messageExecute) {
        GlobalSettingsTransformer mainGroupSettings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, context.guild)
            .getGlobalSettings();
        List <Guild> guilds = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(mainGroupSettings.getMainGroupId(), true);

        GlobalModlogType type = GlobalModlogType.GLOBAL_WATCH;

        try {
            GlobalModlogAction modlogAction = new GlobalModlogAction(
                type, context.getAuthor(), user,
                "\n" + "Automod, moderative action from " + context.getGuild().getName()
            );

            String caseId = GlobalModlog.log(avaire, context, modlogAction);
            GlobalModlog.notifyUser(user, mainGroupSettings, modlogAction, caseId);

            avaire.getGlobalWatchManager().registerGlobalWatch(context.getGuild().getIdLong(), caseId, mainGroupSettings.getMainGroupId(), user.getIdLong(), null);

            for (Guild g : guilds) {
                if (g.getId().equals(context.getGuild().getId())) {
                    runBan(context, user, messageExecute);
                    continue;
                }

                GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, g);
                if (transformer.getOnWatchRole() == 0) {
                    continue;
                }

                Member m = g.getMember(user);
                if (m == null) {
                    continue;
                }

                Role watchRole = g.getRoleById(transformer.getOnWatchRole());
                if (watchRole == null) {
                    continue;
                }

                if (!g.getSelfMember().canInteract(watchRole)) {
                    continue;
                }

                if (transformer.getOnWatchChannel() != 0) {
                    ModlogAction localAction = new ModlogAction(
                        ModlogType.ON_WATCH, context.getAuthor(), user,
                        "\n" + "Automod, moderative action from " + context.getGuild().getName()
                    );

                    Watchlog.log(avaire, g, localAction);
                }

                g.addRoleToMember(m, watchRole).queue();
            }
        } catch (SQLException e) {
            Xeus.getLogger().error(e.getMessage(), e);
            context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
        }
        messageExecute.editMessageEmbeds(context.makeSuccess(":target is banned from this server and is being watched :time in other guilds.")
            .set("target", user.getAsMention())
            .set("time", "permanently").buildEmbed()).queue();
    }

    private void runBan(CommandMessage context, User user, Message edit) {
        String reason = "User has reached 12 warnings and is banned from this guild due to that, reaching 15 warnings will put them on a global-ban.";
        ModlogType type = ModlogType.BAN;
        ModlogAction modlogAction = new ModlogAction(type, context.getAuthor(), user, "\n" + reason);
        String caseId = Modlog.log(avaire, context, modlogAction);
        Modlog.notifyUser(user, context.getGuild(), modlogAction, caseId);


        context.guild.ban(user, 0, String.format("%s - %s#%s (%s)", reason,
                context.getAuthor().getName(), context.getAuthor().getDiscriminator(), context.getAuthor().getId()))
            .queue(aVoid -> edit.editMessageEmbeds(context.makeSuccess(":target has been banned :time")
                    .set("target", user.getAsMention())
                    .set("time", "permenantly").buildEmbed()).queue(),
                throwable -> edit.editMessageEmbeds(context.makeWarning("Failed to ban :targer with error `:error`")
                    .set("target", user.getName() + "#" + user.getDiscriminator())
                    .set("error", throwable.getMessage()).buildEmbed()).queue());
    }

    private void selectWatchOrMuteQueue(CommandMessage context, WarningGrade grade, User user, Message messageExecute, int size) {
        Button gmute7 = Button.primary("globalmute7:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Mute (1 week)");
        Button gwatch7 = Button.primary("globalwatch7:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Watch (1 week)");
        Button gmute14 = Button.primary("globalmute14:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Mute (2 weeks)");
        Button gwatch14 = Button.primary("globalwatch14:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Global Watch (2 weeks)");
        Button cancel = Button.danger("cancel:" + context.getChannel().getId() + ":" + context.getAuthor().getId() + ":" + context.getMessage().getId(),
            "Cancel");


        if (!(grade.isGlobalMute() && size >= 6)) gmute7 = gmute7.asDisabled();
        if (!(grade.isGlobalMute() && size >= 6)) gwatch7 = gwatch7.asDisabled();

        if (!(grade.isGlobalWatch() && size >= 9)) gmute14 = gmute14.asDisabled();
        if (!(grade.isGlobalWatch() && size >= 9)) gwatch14 = gwatch14.asDisabled();

        messageExecute.editMessageEmbeds(new EmbedBuilder().setDescription("What time and punishment should be assigned?").build())
            .setActionRows(ActionRow.of(gmute7, gwatch7), ActionRow.of(gmute14, gwatch14), ActionRow.of(cancel))
            .queue(l -> listenForInteraction(messageExecute, context, user, size, grade));

    }

    private void muteUserQueue(CommandMessage context, Carbon expiresAt, User user, Message messageExecute) {

        GlobalSettingsTransformer mainGroupSettings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, context.guild)
            .getGlobalSettings();
        List <Guild> guilds = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(mainGroupSettings.getMainGroupId(), true);
        String reason = "Automod, moderative action from " + context.getGuild().getName();
        GlobalModlogType type = expiresAt == null ? GlobalModlogType.GLOBAL_MUTE : GlobalModlogType.GLOBAL_TEMP_MUTE;

        try {
            GlobalModlogAction modlogAction = new GlobalModlogAction(
                type, context.getAuthor(), user,
                expiresAt != null
                    ? expiresAt.toDayDateTimeString() + " (" + expiresAt.diffForHumans(true) + ")" + "\n" + reason
                    : "\n" + reason
            );

            String caseId = GlobalModlog.log(avaire, context, modlogAction);
            GlobalModlog.notifyUser(user, mainGroupSettings, modlogAction, caseId);

            avaire.getGlobalMuteManager().registerGlobalMute(context.getGuild().getIdLong(), caseId, mainGroupSettings.getMainGroupId(), user.getIdLong(), expiresAt);

            for (Guild g : guilds) {
                GuildTransformer transformer = GuildController.fetchGuild(avaire, g);
                if (transformer.getMuteRole() == null) {
                    continue;
                }

                Member m = g.getMember(user);
                if (m == null) {
                    continue;
                }

                Role muteRole = g.getRoleById(transformer.getMuteRole());
                if (muteRole == null) {
                    continue;
                }

                if (!g.getSelfMember().canInteract(muteRole)) {
                    continue;
                }

                if (transformer.getModlog() != null) {
                    ModlogAction localAction = new ModlogAction(
                        ModlogType.TEMP_MUTE, context.getAuthor(), user,
                        expiresAt != null
                            ? expiresAt.toDayDateTimeString() + " (" + expiresAt.diffForHumans(true) + ")" + "\n"
                            + reason : "\n" + reason
                    );

                    Modlog.log(avaire, g, localAction);
                }

                g.addRoleToMember(m, muteRole).queue();
            }
        } catch (SQLException e) {
            Xeus.getLogger().error(e.getMessage(), e);
            context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
        }
        messageExecute.editMessageEmbeds(context.makeSuccess(":target has been muted :time!")
            .set("target", user.getAsMention())
            .set("time", expiresAt == null
                ? context.i18n("time.permanently")
                : "for " + expiresAt.diffForHumans(true)).buildEmbed()).queue();
    }

    private void watchUserQueue(CommandMessage context, Carbon expiresAt, User user, Message messageExecute) {
        GlobalSettingsTransformer mainGroupSettings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, context.guild)
            .getGlobalSettings();
        List <Guild> guilds = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(mainGroupSettings.getMainGroupId(), true);
        String reason = "Automod, moderative action from " + context.getGuild().getName();
        GlobalModlogType type = expiresAt == null ? GlobalModlogType.GLOBAL_WATCH : GlobalModlogType.GLOBAL_TEMP_WATCH;

        try {
            GlobalModlogAction modlogAction = new GlobalModlogAction(
                type, context.getAuthor(), user,
                expiresAt != null
                    ? expiresAt.toDayDateTimeString() + " (" + expiresAt.diffForHumans(true) + ")" + "\n" + reason
                    : "\n" + reason
            );

            String caseId = GlobalModlog.log(avaire, context, modlogAction);
            GlobalModlog.notifyUser(user, mainGroupSettings, modlogAction, caseId);

            avaire.getGlobalWatchManager().registerGlobalWatch(context.getGuild().getIdLong(), caseId, mainGroupSettings.getMainGroupId(), user.getIdLong(), expiresAt);

            for (Guild g : guilds) {
                GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, g);
                if (transformer.getOnWatchRole() == 0) {
                    continue;
                }

                Member m = g.getMember(user);
                if (m == null) {
                    continue;
                }

                Role watchRole = g.getRoleById(transformer.getOnWatchRole());
                if (watchRole == null) {
                    continue;
                }

                if (!g.getSelfMember().canInteract(watchRole)) {
                    continue;
                }

                if (transformer.getOnWatchChannel() != 0) {
                    ModlogAction localAction = new ModlogAction(
                        ModlogType.TEMP_ON_WATCH, context.getAuthor(), user,
                        expiresAt != null
                            ? expiresAt.toDayDateTimeString() + " (" + expiresAt.diffForHumans(true) + ")" + "\n"
                            + reason : "\n" + reason
                    );

                    Watchlog.log(avaire, g, localAction);
                }

                g.addRoleToMember(m, watchRole).queue();
            }
        } catch (SQLException e) {
            Xeus.getLogger().error(e.getMessage(), e);
            context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
        }
        messageExecute.editMessageEmbeds(context.makeSuccess(":target is being watched :time!")
            .set("target", user.getAsMention())
            .set("time", expiresAt == null
                ? context.i18n("time.permanently")
                : "for " + expiresAt.diffForHumans(true)).buildEmbed()).queue();
    }


    private final Pattern timeRegEx = Pattern.compile("([0-9]+[w|d|h|m|s])");

    private Carbon parseTime(String string) {
        Matcher matcher = timeRegEx.matcher(string);
        if (!matcher.find()) {
            return null;
        }

        Carbon time = Carbon.now().addSecond();
        do {
            String group = matcher.group();

            String type = group.substring(group.length() - 1);
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

    public List <Guild> getGuildsByMainGroupId(Long mainGroupId) {
        return getGuildsByMainGroupId(mainGroupId, false);
    }

    public List <Guild> getGuildsByMainGroupId(Long mainGroupId, boolean isOfficial) {
        List <Guild> guildList = new LinkedList <>();
        try {
            Collection guildQuery = avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
                .where("main_group_id", mainGroupId)
                .where(builder -> {
                    if (isOfficial) {
                        builder.where("official_sub_group", 1);
                    }
                })
                .get();

            for (DataRow dataRow : guildQuery) {
                Guild guild = avaire.getShardManager().getGuildById(dataRow.getString("id"));
                if (guild != null) {
                    guildList.add(guild);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return guildList;
    }

    private String generateReason(String[] args) {
        return args.length == 0 ?
            "No reason was given." :
            String.join(" ", args);
    }
}
