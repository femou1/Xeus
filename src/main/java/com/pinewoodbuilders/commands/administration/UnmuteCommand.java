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

package com.pinewoodbuilders.commands.administration;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.*;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
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

public class UnmuteCommand extends MuteableCommand {

    public UnmuteCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Unmute Command";
    }

    @Override
    public String getDescription(@Nullable CommandContext context) {
        return String.format(
            "Unmutes the mentioned user by removing the %s role from them, this action will be reported to any channel that has modloging enabled.",
            getMuteRoleNameFromContext(context)
        );
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command <user> [reason]` - Unmutes the given user."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command @Senither` - Unmutes the user with no reason given.",
            "`:command @Senither Calmed down` - Unmutes the user with the given reason."
        );
    }

    @Override
    public List<Class<? extends Command>> getRelations() {
        return Arrays.asList(
            MuteCommand.class,
            MuteRoleCommand.class
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("unmute");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
            "isGuildHROrHigher",
            "require:bot,general.manage_roles",
            "throttle:guild,1,4"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.MODERATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "errors.errorOccurredWhileLoading", "server settings");
        }

        if (args.length == 0) {
            return sendErrorMessage(context, "errors.missingArgument", "user");
        }

        User user = MentionableUtil.getUser(context, args);
        if (user == null) {
            return sendErrorMessage(context, context.i18n("invalidUserMentioned"));
        }

        GuildSettingsTransformer globalSettingsTransformer = context.getGuildSettingsTransformer();
        if (globalSettingsTransformer != null) {
            long mgi = globalSettingsTransformer.getMainGroupId() != 0 ? globalSettingsTransformer.getMainGroupId() : 0;
            if (avaire.getGlobalMuteManager().isGlobalMuted(mgi, user.getIdLong(), context.getGuild().getIdLong())) {
                return sendErrorMessage(context, "This user has a global mute on their name, hence you cannot unmute this person. If you are a MGM, please use `gm um` to this person across all connected guilds.");
            }
        }

        if (transformer.getModlog() == null) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requiresModlogToBeSet", prefix));
        }

        if (transformer.getMuteRole() == null) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requireMuteRoleToBeSet", prefix));
        }

        Role muteRole = context.getGuild().getRoleById(transformer.getMuteRole());
        if (muteRole == null) {
            String prefix = generateCommandPrefix(context.getMessage());
            return sendErrorMessage(context, context.i18n("requireMuteRoleToBeSet", prefix));
        }

        if (!context.getGuild().getSelfMember().canInteract(muteRole)) {
            return sendErrorMessage(context, context.i18n("muteRoleIsPositionedHigher", muteRole.getAsMention()));
        }


        if (!RoleUtil.hasRole(context.getGuild().getMember(user), muteRole)) {
            return sendErrorMessage(context, context.i18n("userDoesntHaveMuteRole", user.getAsMention()));
        }



        String reason = generateMessage(Arrays.copyOfRange(args, 1, args.length));
        context.getGuild().removeRoleFromMember(
            context.getGuild().getMember(user), muteRole
        ).reason(reason).queue(aVoid -> {
            ModlogAction modlogAction = new ModlogAction(
                ModlogType.UNMUTE, context.getAuthor(), user, reason
            );

            String caseId = Modlog.log(avaire, context, modlogAction);
            Modlog.notifyUser(user, context.getGuild(), modlogAction, caseId);

            try {
                avaire.getMuteManger().unregisterMute(context.getGuild().getIdLong(), user.getIdLong());

                context.makeSuccess(context.i18n("userHasBeenUnmuted"))
                    .set("target", user.getAsMention())
                    .queue();
            } catch (SQLException e) {
                Xeus.getLogger().error(e.getMessage(), e);
                context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
            }
        });

        return true;
    }

    private String generateMessage(String[] args) {
        return args.length == 0 ?
            "No reason was given." :
            String.join(" ", args);
    }
}
