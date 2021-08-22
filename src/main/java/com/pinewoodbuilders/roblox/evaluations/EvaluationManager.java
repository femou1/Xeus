package com.pinewoodbuilders.roblox.evaluations;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.contracts.cache.CacheAdapter;
import com.pinewoodbuilders.contracts.roblox.evaluations.PassedEvals;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.roblox.RobloxAPIManager;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

public class EvaluationManager {

    public final CacheAdapter cooldownCache = Xeus.getInstance().getCache().getAdapter(CacheType.FILE);

    private final Xeus avaire;
    private final RobloxAPIManager manager;

    public EvaluationManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.manager = robloxAPIManager;
    }

    public List <PassedEvals> getPassedEvals(Long robloxId) {
        if (avaire.getRobloxAPIManager().getUserAPI().getUsername(robloxId) == null) {
            return null;
        }
        List <PassedEvals> pe = new LinkedList <>();
        try {
            Collection c = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", robloxId).get();
            if (c.isEmpty()) {
                return pe;
            }

            c.forEach(pes -> {
                if (pes.getBoolean("passed_quiz")) {
                    pe.add(PassedEvals.COMBAT);
                }
                if (pes.getBoolean("passed_patrol")) {
                    pe.add(PassedEvals.PATROL);
                }
                if (pes.getBoolean("passed_combat")) {
                    pe.add(PassedEvals.COMBAT);
                }
            });
            return pe;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return null;
        }
    }

    public boolean setStatus(Long robloxId, PassedEvals eval, boolean isPassed) {
        String username = manager.getUserAPI().getUsername(robloxId);
        if (username == null) {
            return false;
        }

        if (eval == null) {
            return false;
        }

        QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.EVALS_DATABASE_TABLE_NAME).where("roblox_id", robloxId);
        try {
            if (getPassedEvals(robloxId) != null) {
                qb.update(statement -> {
                    statement.set(eval.getName(), isPassed).set("roblox_username", username);
                });
            } else {
                qb.insert(statement -> {
                    statement.set(eval.getName(), isPassed).set("roblox_username", username);
                });
            }
            return true;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
    }

    public boolean addQuizToDatabase(Long robloxId, Long guildId, Long messageId) {
        String username = manager.getUserAPI().getUsername(robloxId);
        if (username == null) {
            return false;
        }

        try {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.PENDING_QUIZ_TABLE_NAME).where("roblox_id", robloxId);
            if (qb.get().isEmpty()) {
                qb.insert(statement -> {
                    statement.set("roblox_id", robloxId).set("message_id", messageId).set("server_id", guildId);
                });
                return true;
            } else {
                return false;
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
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
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
    }

    public CacheAdapter getCooldownCache() {
        return cooldownCache;
    }
}
