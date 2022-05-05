package com.pinewoodbuilders.roblox.evaluations;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.contracts.cache.CacheAdapter;
import com.pinewoodbuilders.contracts.roblox.evaluations.EvaluationStatus;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.roblox.RobloxAPIManager;

import java.sql.SQLException;

public class EvaluationManager {

    public final CacheAdapter cooldownCache = Xeus.getInstance().getCache().getAdapter(CacheType.FILE);

    private final Xeus avaire;
    private final RobloxAPIManager manager;

    public EvaluationManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
    }

    public EvaluationStatus getEvaluationStatus(Long robloxId) {
        try {
            Collection qb = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", robloxId).get();

            if (qb.isEmpty()) {
                return new EvaluationStatus(false, false, false, null, null, null);
            }

            if (qb.size() > 1) {
                return new EvaluationStatus(false, false, false, null, null, null);
            }

            DataRow dr = qb.get(0);
            return new EvaluationStatus(
                dr.getBoolean("passed_quiz"),
                dr.getBoolean("passed_combat"),
                dr.getBoolean("passed_consensus"),
                dr.getString("evaluator"),
                dr.getTimestamp("updated_at"),
                dr.getTimestamp("created_at")
            );
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return new EvaluationStatus(false, false, false, null, null, null);
    }



    public void addQuizToDatabase(Long robloxId, Long guildId, Long messageId) {
        String username = manager.getUserAPI().getUsername(robloxId);
        if (username == null) {
            return;
        }

        try {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("roblox_id", robloxId);
            if (!qb.get().isEmpty()) {return;}
            qb.insert(statement -> {
                statement.set("roblox_id", robloxId).set("message_id", messageId).set("server_id", guildId);
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public boolean hasPendingQuiz(Long robloxId) {
        String username = manager.getUserAPI().getUsername(robloxId);
        if (username == null) {
            return false;
        }

        try {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("roblox_id", robloxId);
            return !qb.get().isEmpty();
        } catch (SQLException throwable) {
            throwable.printStackTrace();
            return false;
        }
    }

    public CacheAdapter getCooldownCache() {
        return cooldownCache;
    }
}
