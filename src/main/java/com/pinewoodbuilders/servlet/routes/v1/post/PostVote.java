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

package com.pinewoodbuilders.servlet.routes.v1.post;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.metrics.Metrics;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.vote.VoteCacheEntity;
import com.pinewoodbuilders.vote.VoteMetricType;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class PostVote extends SparkRoute {

    private static final Logger log = LoggerFactory.getLogger(PostVote.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        log.info("Vote route has been hit by {} with the body: {}",
            request.ip(), request.body()
        );

        if (!hasValidAuthorizationHeader(request)) {
            log.warn("Unauthorized request, missing or invalid \"Authorization\" header give.");
            return buildResponse(response, 401, "Unauthorized request, missing or invalid \"Authorization\" header give.");
        }

        VoteRequest voteRequest = Xeus.gson.fromJson(request.body(), VoteRequest.class);

        if (!isValidVoteRequest(voteRequest)) {
            log.warn("Bad request, invalid JSON data given to justify a upvote request.");
            return buildResponse(response, 400, "Bad request, invalid JSON data given to justify a upvote request.");
        }

        Xeus.getInstance().getVoteManager().registerVoteFor(
            Long.valueOf(voteRequest.user),
            voteRequest.isWeekend ? 2 : 1
        );

        VoteCacheEntity voteEntity = Xeus.getInstance().getVoteManager()
            .getVoteEntityWithFallback(Long.valueOf(voteRequest.user));
        voteEntity.setCarbon(Carbon.now().addHours(12));

        Metrics.dblVotes.labels(VoteMetricType.WEBHOOK.getName()).inc();

        User userById = Xeus.getInstance().getShardManager().getUserById(voteRequest.user);
        if (userById == null || userById.isBot()) {
            log.info("Vote has been registered by {} [No servers is shared with this user]",
                voteRequest.user
            );

            return buildResponse(response, 200, "Vote registered, thanks for voting!");
        }

        log.info("Vote has been registered by {} ({})",
            userById.getAsTag(), userById.getId()
        );

        if (!voteEntity.isOptIn()) {
            return buildResponse(response, 200, "Vote registered, thanks for voting!");
        }

        Xeus.getInstance().getVoteManager().getMessenger().SendThanksForVotingMessageInDM(userById, voteEntity.getVotePoints());

        return buildResponse(response, 200, "Vote registered, thanks for voting!");
    }

    private boolean isValidVoteRequest(VoteRequest request) {
        if (request == null) {
            return false;
        }

        if (request.bot == null || request.user == null || request.type == null) {
            return false;
        }

        if (!request.bot.equals(Xeus.getInstance().getSelfUser().getId())) {
            return false;
        }

        return request.type.equalsIgnoreCase("upvote");
    }

    private class VoteRequest {
        private String bot;
        private String user;
        private String type;
        private boolean isWeekend;
    }
}
