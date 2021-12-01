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
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.metrics.Metrics;
import com.pinewoodbuilders.requests.Response;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.vote.VoteCacheEntity;
import com.pinewoodbuilders.vote.VoteEntity;
import com.pinewoodbuilders.vote.VoteMetricType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

public class DrainVoteQueueTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DrainVoteQueueTask.class);

    @Override
    public void handle(Xeus avaire) {
        if (avaire.getVoteManager() == null || avaire.getVoteManager().getQueue().isEmpty()) {
            return;
        }

        VoteEntity entity = avaire.getVoteManager().getQueue().poll();
        if (entity == null) {
            return;
        }

        if (avaire.getConfig().getBoolean("vote-lock.sync-with-public-bot", false)) {
            RequestFactory.makeGET("http://api.avairebot.com/v1/votes/" + entity.getUserId())
                .send((Consumer<Response>) response -> acceptViaPublicSync(avaire, response, entity));
        }

        String apiToken = avaire.getConfig().getString("vote-lock.vote-sync-token");
        if (apiToken == null || apiToken.trim().length() == 0) {
            return;
        }

        if (apiToken.equalsIgnoreCase("ReplaceThisWithYourAPITokenForDBL")) {
            return;
        }

        log.info("Checking vote requests for {} with the DBL API...", entity.getUserId());

        RequestFactory.makeGET("https://discordbots.org/api/bots/275270122082533378/check")
            .addParameter("userId", entity.getUserId())
            .addHeader("Authorization", avaire.getConfig().getString("vote-lock.vote-sync-token"))
            .send((Consumer<Response>) response -> acceptViaDBL(avaire, response, entity));
    }

    private void acceptViaPublicSync(Xeus avaire, Response response, VoteEntity entity) {
        if (response.getResponse().code() != 200) {
            return;
        }

        Object obj = response.toService(Map.class);
        if (!(obj instanceof Map)) {
            return;
        }

        Map<String, Boolean> data = (Map<String, Boolean>) obj;
        if (data.isEmpty()) {
            return;
        }

        Boolean result = data.getOrDefault(String.valueOf(entity.getUserId()), false);
        if (result == null || !result) {
            return;
        }

        Metrics.dblVotes.labels(VoteMetricType.COMMAND.getName()).inc();

        Carbon expiresIn = new Carbon(response.getResponse().header("Date")).addHours(12);

        log.info("Vote record for {} was found, registering vote that expires on {}", entity.getUserId(), expiresIn.toDateTimeString());

        User user = avaire.getShardManager().getUserById(entity.getUserId());
        if (user == null) {
            return;
        }

        handleRegisteringVote(avaire, user, expiresIn, entity);
    }

    private void acceptViaDBL(Xeus avaire, Response response, VoteEntity entity) {
        if (response.getResponse().code() != 200) {
            return;
        }

        Object obj = response.toService(Map.class);
        if (!(obj instanceof Map)) {
            return;
        }

        Map<String, Object> data = (Map<String, Object>) obj;
        if (data.isEmpty()) {
            return;
        }

        int voted = NumberUtil.parseInt(data.getOrDefault("voted", "0.0")
            .toString().split("\\.")[0]);

        if (voted != 1) {
            return;
        }

        Metrics.dblVotes.labels(VoteMetricType.COMMAND.getName()).inc();

        Carbon expiresIn = new Carbon(response.getResponse().header("Date")).addHours(12);

        log.info("Vote record for {} was found, registering vote that expires on {}", entity.getUserId(), expiresIn.toDateTimeString());

        User user = avaire.getShardManager().getUserById(entity.getUserId());
        if (user == null) {
            return;
        }

        handleRegisteringVote(avaire, user, expiresIn, entity);
    }

    private void handleRegisteringVote(Xeus avaire, User user, Carbon expiresIn, VoteEntity entity) {
        VoteCacheEntity voteEntity = avaire.getVoteManager().getVoteEntityWithFallback(user);
        voteEntity.setCarbon(expiresIn);

        avaire.getVoteManager().registerVoteFor(user.getIdLong(), 1);

        log.info("Vote has been registered by {} ({})",
            user.getName() + "#" + user.getDiscriminator(), user.getId()
        );

        TextChannel textChannel = avaire.getShardManager().getTextChannelById(entity.getChannelId());
        if (textChannel == null || !textChannel.canTalk()) {
            if (voteEntity.isOptIn()) {
                avaire.getVoteManager().getMessenger()
                    .SendThanksForVotingMessageInDM(user, voteEntity.getVotePoints());
            }
            return;
        }

        textChannel.sendMessageEmbeds(
            avaire.getVoteManager().getMessenger().buildThanksForVotingMessage(
                "Your vote has been registered!", voteEntity.getVotePoints()
            )
        ).queue(null, error -> {
            if (voteEntity.isOptIn()) {
                avaire.getVoteManager().getMessenger()
                    .SendThanksForVotingMessageInDM(user, voteEntity.getVotePoints());
            }
        });
    }
}
