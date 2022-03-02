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

package com.pinewoodbuilders.scheduler.tasks;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;

import java.util.Arrays;

public class ChangeGameTask implements Task {

    public static boolean hasCustomStatus = false;

    private int index = 0;

    @Override
    public void handle(Xeus avaire) {
        if (hasCustomStatus || !avaire.areWeReadyYet()) {
            return;
        }

        if (index >= avaire.getConfig().getStringList("playing").size()) {
            index = 0;
        }

        String playing = avaire.getConfig().getStringList("playing").get(index);

        if (playing.trim().length() != 0) {
            for (JDA shard : avaire.getShardManager().getShards()) {
                shard.getPresence().setActivity(getGameFromType(avaire, playing, shard));
            }
        } else {
            avaire.getShardManager().setActivity(null);
        }

        index++;
    }

    private Activity getGameFromType(Xeus avaire, String status, JDA shard) {
        if (!status.contains(":")) {
            return Activity.playing(formatGame(avaire, status, shard));
        }

        String[] split = status.split(":");
        status = String.join(":", Arrays.copyOfRange(split, 1, split.length));
        return switch (split[0].toLowerCase()) {
            case "listen", "listening" -> Activity.listening(formatGame(avaire, status, shard));
            case "watch", "watching" -> Activity.watching(formatGame(avaire, status, shard));
            case "stream", "streaming" -> Activity.streaming(formatGame(avaire, status, shard), "https://www.twitch.tv/codixer");
            default -> Activity.playing(formatGame(avaire, status, shard));
        };
    }

    private String formatGame(Xeus avaire, String game, JDA shard) {
        game = game.replaceAll("%users%", NumberUtil.formatNicely(avaire.getShardEntityCounter().getUsers()));
        game = game.replaceAll("%guilds%", NumberUtil.formatNicely(avaire.getShardEntityCounter().getGuilds()));

        game = game.replaceAll("%shard%", shard.getShardInfo().getShardString());
        game = game.replaceAll("%shard-id%", "" + shard.getShardInfo().getShardId());
        game = game.replaceAll("%shard-total%", "" + shard.getShardInfo().getShardTotal());

        return game;
    }
}
