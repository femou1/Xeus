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

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.ComparatorUtil;
import com.pinewoodbuilders.utilities.MentionableUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AuditLogChannelCommand extends Command {

    private static final Logger log = LoggerFactory.getLogger(AuditLogChannelCommand.class);

    public AuditLogChannelCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Audit Log Command";
    }

    @Override
    public String getDescription() {
        return "Set the audit log channel";
    }


    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command set-channel <#channel>` - Set an audit log channel."
        );
    }
    @Override
    public List<String> getTriggers() {
        return Arrays.asList("audit-log", "al");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isGuildLeadership",
            "throttle:user,1,5"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.LEVEL_AND_EXPERIENCE);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer guildTransformer = context.getGuildSettingsTransformer();
        if (args.length < 1) {
            context.makeError("Please choose your argument...\n- ``set-channel``\n- ``- ``set-join-logs``").queue();
            return false;
        }

        switch (args[0].toLowerCase()){
            case "sc":
            case "set-channel":
                return runVoteUpdateChannelChannelCommand(context, args, guildTransformer);
            case "set-join-logs":
                return runJoinLogsUpdateChannelChannelCommand(context, args, guildTransformer);
            default:
                return sendErrorMessage(context, "No valid argument given");
        }
    }

    private boolean runVoteUpdateChannelChannelCommand(CommandMessage context, String[] args, GuildSettingsTransformer transformer) {
        if (transformer == null) {
            return sendErrorMessage(context, "The guildtransformer can't be found :(");
        }

        if (args.length == 1) {
            sendVoteValidationChannel(context, transformer).queue();
            return true;
        }

        if (ComparatorUtil.isFuzzyFalse(args[1])) {
            return disableVoteValidation(context, transformer);
        }

        GuildChannel channel = MentionableUtil.getChannel(context.getMessage(), args, 1);
        if (!(channel instanceof TextChannel)) {
            return sendErrorMessage(context, "You must mentions a channel, or call out it's exact name!");
        }

        if (!((TextChannel) channel).canTalk() || !context.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
            return sendErrorMessage(context, context.i18n("\"I can't send embedded messages in the specified channel, please change my permission level for the {0} channel if you want to use it as a \"audit log\" channel.", ((TextChannel) channel).getAsMention()));
        }

        try {
            updateVoteValidation(transformer, context, channel.getIdLong());

            context.makeSuccess("The audit log channel is set to :channel this guild.")
                .set("channel", ((TextChannel) channel).getAsMention())
                .queue();
        } catch (SQLException ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }
        return true;
    }

    private boolean disableVoteValidation(CommandMessage context, GuildSettingsTransformer transformer) {
        try {
            updateVoteValidation(transformer, context, 0);

            context.makeSuccess("The audit log channel has been disabled on this guild.")
                .queue();
        } catch (SQLException ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }

        return true;
    }

    private PlaceholderMessage sendVoteValidationChannel(CommandMessage context, GuildSettingsTransformer transformer) {
        if (transformer.getAuditLogsChannelId() == 0) {
            return context.makeWarning("The audit log channel is disabled on this guild.");
        }

        GuildChannel modlogChannel = context.getGuild().getTextChannelById(transformer.getAuditLogsChannelId());
        if (modlogChannel == null) {
            try {
                updateVoteValidation(transformer, context, 0);
            } catch (SQLException ex) {
                Xeus.getLogger().error(ex.getMessage(), ex);
            }
            return context.makeInfo("The audit log channel is disabled on this guild.");
        }

        return context.makeSuccess("The audit log channel is set to :channel this guild.")
            .set("channel", modlogChannel.getAsMention());
    }

    private void updateVoteValidation(GuildSettingsTransformer transformer, CommandMessage context, long value) throws SQLException {
        transformer.setAuditLogsChannelId(value);
        avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
            .where("id", context.getGuild().getId())
            .update(statement -> statement.set("audit_logs_channel_id", value));
    }

    private boolean runJoinLogsUpdateChannelChannelCommand(CommandMessage context, String[] args, GuildSettingsTransformer transformer) {
        if (transformer == null) {
            return sendErrorMessage(context, "The guildtransformer can't be found :(");
        }

        if (args.length == 1) {
            sendJoinLogsChannel(context, transformer).queue();
            return true;
        }

        if (ComparatorUtil.isFuzzyFalse(args[1])) {
            return disableJoinLogs(context, transformer);
        }

        GuildChannel channel = MentionableUtil.getChannel(context.getMessage(), args, 1);
        if (!(channel instanceof TextChannel)) {
            return sendErrorMessage(context, "You must mentions a channel, or call out it's exact name!");
        }

        if (!((TextChannel) channel).canTalk() || !context.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
            return sendErrorMessage(context, context.i18n("\"I can't send embedded messages in the specified channel, please change my permission level for the {0} channel if you want to use it as a \"Join Logs\" channel.", ((TextChannel) channel).getAsMention()));
        }

        try {
            updateJoinLogs(transformer, context, channel.getIdLong());

            context.makeSuccess("The join log channel is set to :channel this guild.")
                .set("channel", ((TextChannel) channel).getAsMention())
                .queue();
        } catch (SQLException ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }
        return true;
    }

    private boolean disableJoinLogs(CommandMessage context, GuildSettingsTransformer transformer) {
        try {
            updateJoinLogs(transformer, context, 0);

            context.makeSuccess("The join logs channel has been disabled on this guild.")
                .queue();
        } catch (SQLException ex) {
            Xeus.getLogger().error(ex.getMessage(), ex);
        }

        return true;
    }

    private PlaceholderMessage sendJoinLogsChannel(CommandMessage context, GuildSettingsTransformer transformer) {
        if (transformer.getJoinLogs() == 0) {
            return context.makeWarning("The join log channel is disabled on this guild.");
        }

        TextChannel modlogChannel = context.getGuild().getTextChannelById(transformer.getJoinLogs());
        if (modlogChannel == null) {
            try {
                updateJoinLogs(transformer, context, 0);
            } catch (SQLException ex) {
                Xeus.getLogger().error(ex.getMessage(), ex);
            }
            return context.makeInfo("The join log channel is disabled on this guild.");
        }

        return context.makeSuccess("The join log channel is set to :channel this guild.")
            .set("channel", modlogChannel.getAsMention());
    }

    private void updateJoinLogs(GuildSettingsTransformer transformer, CommandMessage context, long value) throws SQLException {
        transformer.setJoinLogs(value);
        avaire.getDatabase().newQueryBuilder(Constants.GUILD_SETTINGS_TABLE)
            .where("id", context.getGuild().getId())
            .update(statement -> statement.set("join_logs", value));
    }

}
