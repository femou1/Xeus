/*
 * Copyright (c) 2019.
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

package com.pinewoodbuilders.commands.onwatch;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.*;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.modlog.local.watchlog.Watchlog;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UnWatchCommand extends OnWatchableCommand {

    public UnWatchCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Unwatch Command";
    }

    @Override
    public String getDescription(@Nullable CommandContext context) {
        return String.format(
            "Unwatches the mentioned user by removing the %s role from them, this action will be reported to any channel that has modloging enabled.",
            getOnWatchRoleFromContext(context)
        );
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <user> [reason]` - Unwatchs the given user."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList(
            "`:command @Senither` - Unwatchs the user with no reason given.",
            "`:command @Senither Calmed down` - Unwatchs the user with the given reason."
        );
    }

    @Override
    public List <Class <? extends Command>> getRelations() {
        return Arrays.asList(
            OnWatchCommand.class,
            WatchRoleCommand.class
        );
    }

    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("unwatch");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isPinewoodGuild",
            "isGuildHROrHigher",
            "require:bot,general.manage_roles",
            "throttle:guild,1,4"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.ON_WATCH);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildSettingsTransformer transformer = context.getGuildSettingsTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        User user = MentionableUtil.getUser(context, args);
        if (user == null) {
            return sendErrorMessage(context, context.i18n("invalidUserMentioned"));
        }

        GuildSettingsTransformer globalSettingsTransformer = context.getGuildSettingsTransformer();
        if (globalSettingsTransformer != null) {
            long mgi = globalSettingsTransformer.getMainGroupId() != 0 ? globalSettingsTransformer.getMainGroupId() : 0;
            if (avaire.getGlobalWatchManager().isGlobalWatched(mgi, user.getIdLong(), context.getGuild().getIdLong())) {
                return sendErrorMessage(context, "This user has a global watch on their name, hence you cannot unwatch this person. If you are a MGM, please use `gm uw` to this person across all connected guilds.");
            }
        }

        if (transformer.getOnWatchChannel() == 0) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requiresModlogToBeSet", prefix));
        }

        if (transformer.getOnWatchRole() == 0) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requireMuteRoleToBeSet", prefix));
        }

        Role watchRole = context.getGuild().getRoleById(transformer.getOnWatchRole());
        if (watchRole == null) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requireMuteRoleToBeSet", prefix));
        }

        if (!context.getGuild().getSelfMember().canInteract(watchRole)) {
            return sendErrorMessage(context, context.i18n("watchRoleIsPositionedHigher", watchRole.getAsMention()));
        }

        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "user");
        }



        if (!RoleUtil.hasRole(context.getGuild().getMember(user), watchRole)) {
            return sendErrorMessage(context, context.i18n("userDoesntHaveMuteRole", user.getAsMention()));
        }

        String reason = generateMessage(Arrays.copyOfRange(args, 1, args.length));
        try {
            avaire.getOnWatchManger().unregisterOnWatch(context.getGuild().getIdLong(), user.getIdLong());

        } catch (SQLException e) {
            Xeus.getLogger().error(e.getMessage(), e);
            context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
        }

        context.getGuild().removeRoleFromMember(
            context.getGuild().getMember(user), watchRole
        ).reason(reason).queue(aVoid -> {
            ModlogAction modlogAction = new ModlogAction(
               ModlogType.UN_ON_WATCH, context.getAuthor(), user, reason
            );

            String caseId = Watchlog.log(avaire, context, modlogAction);
            Watchlog.notifyUser(user, context.getGuild(), modlogAction, caseId);


            context.makeSuccess(context.i18n("userHasBeenUnmuted"))
                .set("target", user.getAsMention())
                .queue();

        });

        return true;
    }

    private String generateMessage(String[] args) {
        return args.length == 0 ?
            "No reason was given." :
            String.join(" ", args);
    }
}
