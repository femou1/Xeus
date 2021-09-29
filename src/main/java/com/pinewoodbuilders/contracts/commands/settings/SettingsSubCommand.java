package com.pinewoodbuilders.contracts.commands.settings;

import java.sql.SQLException;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.settings.GuildAndGlobalSettingsCommand;
import com.pinewoodbuilders.commands.settings.server.ServerSettingsSubCommand;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.query.QueryBuilder;

public abstract class SettingsSubCommand {

    /**
     * The main {@link Xeus avaire} application instance.
     */
    protected final Xeus avaire;

    /**
     * The parent playlist command, used for accessing command specific
     * methods and generating error response messages.
     */
    protected final GuildAndGlobalSettingsCommand command;

    /**
     * Creates a new plugin sub command instance.
     *
     * @param avaire  The main avaire application instance.
     * @param command The parent plugin command instance.
     */
    public SettingsSubCommand(Xeus avaire, GuildAndGlobalSettingsCommand command) {
        this.avaire = avaire;
        this.command = command;
    }

    /**
     * Handles the sub plugin command using the given
     * command context and formatted arguments.
     *
     * @param context The command message context generated using the
     *                JDA message event that invoked the command.
     * @param args    The arguments parsed to the command.
     * @return {@code True} on success, {@code False} on failure.
     */
    public abstract boolean onCommand(CommandMessage context, String[] args);

    /**
     * Add a moderator to the list of mods, this depends on a per argument basis. But discord and roblox id are required;
     *  
     * @param mainGroupId   The main group to give permissions for, can be null if isGlobalAdmin is null.
     * @param discordId     The Discord ID of the user to give permissions to
     * @param robloxId      The roblox ID to give permissions to.
     * @param isGroupLead   The user is a global lead (Facilitator+)
     * @param isGlobalAdmin The user has been voted on becoming a global admin and is allowed to moderate everywhere.
     * @throws SQLException There has gone something wrong with the SQL Statement, debug to see what exactly.
     */
    protected final void addUserToModerationTable(long mainGroupId, Long discordId, long robloxId, boolean isGroupLead, boolean isGlobalAdmin) throws SQLException {
        avaire.getDatabase().newQueryBuilder(Constants.GROUP_MODERATORS_TABLE)
            .insert(statement -> {
                statement.set("discord_id", discordId)
                         .set("roblox_id", robloxId)
                         .set("main_group_id", mainGroupId)
                         .set("is_global_lead", isGroupLead)
                         .set("is_global_admin", isGlobalAdmin);
            });
    }

    /**
     * Remove a moderator from the moderation table, this doesn't depend on a per argument basis, but only the discord user id.
     * 
     * @param discordId The discord ID of the user you want to remove permissions for. 
     * @throws SQLException There has gone something wrong in the SQL statement, debug to see what exactly.
     */
    protected final void removeUserFromModerationTable(Long discordId) throws SQLException {
        avaire.getDatabase().newQueryBuilder(Constants.GROUP_MODERATORS_TABLE).where("discord_id", discordId).delete();
    }

    protected final Collection getModeratorByDiscordId(Long discordId) {
        try {
            Collection s = avaire.getDatabase().newQueryBuilder(Constants.GROUP_MODERATORS_TABLE).where("discord_id", discordId).get();
            if (s.size() > 0) {
                return s;
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        
    }

    protected final Collection getModeratorByRobloxId(Long robloxId) {
        try {
            Collection s = avaire.getDatabase().newQueryBuilder(Constants.GROUP_MODERATORS_TABLE)
                                               .where("roblox_id", robloxId).get();
            if (s.size() > 0) {
                return s;
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected final Collection getModerators() {
        try {
            Collection s = avaire.getDatabase().newQueryBuilder(Constants.GROUP_MODERATORS_TABLE).get();
            if (s.size() > 0) {
                return s;
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
