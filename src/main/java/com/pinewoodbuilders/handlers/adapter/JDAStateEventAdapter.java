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

package com.pinewoodbuilders.handlers.adapter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class JDAStateEventAdapter extends EventAdapter {

    public static final Cache<Long, Long> cache = CacheBuilder.newBuilder()
        .recordStats()
        .expireAfterAccess(3, TimeUnit.MINUTES)
        .build();

    private static final Logger log = LoggerFactory.getLogger(JDAStateEventAdapter.class);

    /**
     * Instantiates the event adapter and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public JDAStateEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public void onConnectToShard(JDA jda) {
        handleAutoroleTask(jda);
        loadSlashCommands(avaire.getShardManager());
    }

    private void loadSlashCommands(ShardManager shardManager) {
        for (JDA shard : shardManager.getShards()) {
            CommandListUpdateAction commands = shard.updateCommands();
            commands.addCommands().queue();
        }

        for (Guild g : shardManager.getGuilds()) {
            if (g.getId().equals("438134543837560832")) {
                g.updateCommands().addCommands(Commands.slash("verify", "Verify yourself on the guild you run this command on."),
                    Commands.slash("update", "Update a user on the guild you run this command on.")
                        .addOption(OptionType.USER, "member", "This will update the specified member to the ranks he has.", true),
                    Commands.slash("whois", "Check the information on a user.")
                        .addOption(OptionType.USER, "member", "This will use the user to get info about their ranks + roblox profile.", true),
                    Commands.slash("roleinfo", "See the information about a Discord role.")
                        .addOption(OptionType.ROLE, "role", "The role you would like to see information about.", true),
                    Commands.user("whois")).queue();

            }
        }
    }

    private void handleAutoroleTask(JDA jda) {
        log.debug("Connection to shard {} has been established, running autorole job to sync autoroles missed due to downtime",
            jda.getShardInfo().getShardId()
        );

        if (cache.asMap().isEmpty()) {
            populateAutoroleCache();
        }

        int updatedUsers = 0;
        long thirtyMinutesAgo = Carbon.now().subMinutes(30).getTimestamp();

        for (Guild guild : jda.getGuilds()) {
            if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                continue;
            }

            Long autoroleId = cache.getIfPresent(guild.getIdLong());
            if (autoroleId == null) {
                continue;
            }

            Role autorole = guild.getRoleById(autoroleId);
            if (autorole == null) {
                continue;
            }

            for (Member member : guild.getMembers()) {
                if (member.getTimeJoined().toEpochSecond() > thirtyMinutesAgo) {
                    if (!RoleUtil.hasRole(member, autorole)) {
                        updatedUsers++;
                        guild.addRoleToMember(member, autorole)
                            .queue();
                    }
                }
            }
        }

        log.debug("Shard {} successfully synced {} new users autorole",
            jda.getShardInfo().getShardId(), updatedUsers
        );
    }

    private void populateAutoroleCache() {
        log.debug("No cache entries was found, populating the auto role cache");
        try {
            for (DataRow row : avaire.getDatabase().query(String.format(
                "SELECT `id`, `autorole` FROM `%s` WHERE `autorole` IS NOT NULL;", Constants.GUILD_TABLE_NAME
            ))) {
                cache.put(row.getLong("id"), row.getLong("autorole"));
            }
        } catch (SQLException e) {
            log.error("Failed to populate the autorole cache: {}", e.getMessage(), e);
        }
    }
}
