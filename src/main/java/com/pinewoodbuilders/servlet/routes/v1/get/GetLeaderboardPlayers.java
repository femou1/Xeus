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

package com.pinewoodbuilders.servlet.routes.v1.get;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.utility.LeaderboardCommand;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GuildController;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.utilities.CacheUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

public class GetLeaderboardPlayers extends SparkRoute {

    @Override
    public Object handle(Request request, Response response) throws Exception {
        try {
            Long guildId = Long.parseLong(request.params("id"));

            Guild guild = Xeus.getInstance().getShardManager().getGuildById(guildId);
            if (guild == null) {
                return buildResponse(response, 404, "Invalid guild ID given, no guild found with the given id.");
            }

            GuildTransformer transformer = GuildController.fetchGuild(Xeus.getInstance(), guild);

            JSONObject root = new JSONObject();
            root.put("id", guild.getId());
            root.put("name", guild.getName());
            root.put("enabled", transformer.isLevels());
            root.put("modifier", transformer.getLevelModifier());

            JSONArray users = new JSONArray();
            JSONArray roles = new JSONArray();

            if (transformer.isLevels()) {
                for (DataRow row : loadTop100(guildId.toString())) {
                    JSONObject user = new JSONObject();
                    user.put("id", row.getString("user_id"));
                    user.put("username", row.getString("username"));
                    user.put("rawUsername", row.getRaw().get("username"));
                    user.put("discriminator", row.getString("discriminator"));
                    user.put("avatar", row.getString("avatar"));
                    user.put("experience", row.getLong("experience"));

                    users.put(user);
                }

                if (!transformer.getLevelRoles().isEmpty()) {
                    ArrayList<Integer> keys = new ArrayList<>(
                        transformer.getLevelRoles().keySet()
                    );

                    Collections.sort(keys);
                    Collections.reverse(keys);

                    for (int level : keys) {
                        Role role = guild.getRoleById(
                            transformer.getLevelRoles().get(level)
                        );

                        if (role == null) {
                            continue;
                        }

                        JSONObject roleObj = new JSONObject();
                        roleObj.put("level", level);
                        roleObj.put("roleId", role.getId());
                        roleObj.put("roleName", role.getName());
                        roleObj.put("roleColor", Integer.toHexString(role.getColorRaw() & 0xffffff));
                        roles.put(roleObj);
                    }
                }
            }

            root.put("leaderboard", users);
            root.put("roles", roles);

            return root;
        } catch (NumberFormatException e) {
            return buildResponse(response, 400, "Invalid guild ID given, the ID must be a number.");
        } catch (RuntimeException e) {
            return buildResponse(response, 404, "Invalid guild ID given, no guild found with the given id.");
        }
    }

    private Collection loadTop100(String guildId) {
        return (Collection) CacheUtil.getUncheckedUnwrapped(LeaderboardCommand.cache, guildId, () -> {
            try {
                return Xeus.getInstance().getDatabase().newQueryBuilder(Constants.PLAYER_EXPERIENCE_TABLE_NAME)
                    .where("guild_id", guildId)
                    .orderBy("experience", "desc")
                    .take(100)
                    .get();
            } catch (SQLException e) {
                return Collection.EMPTY_COLLECTION;
            }
        });
    }
}
