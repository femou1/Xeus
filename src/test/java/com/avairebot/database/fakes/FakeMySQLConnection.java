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

package com.avairebot.database.fakes;

import com.pinewoodbuilders.contracts.database.StatementInterface;
import com.pinewoodbuilders.contracts.database.connections.FilenameDatabase;
import com.pinewoodbuilders.database.DatabaseManager;
import com.pinewoodbuilders.database.grammar.mysql.Create;
import com.pinewoodbuilders.database.grammar.mysql.Delete;
import com.pinewoodbuilders.database.grammar.mysql.Insert;
import com.pinewoodbuilders.database.grammar.mysql.Update;
import com.pinewoodbuilders.database.grammar.sqlite.Select;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.schema.Blueprint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;

public class FakeMySQLConnection extends FilenameDatabase {

    FakeMySQLConnection(DatabaseManager dbm) {
        super(dbm);

        this.setFilename(":memory:");
    }

    @Override
    protected boolean initialize() {
        return false;
    }

    @Override
    protected void queryValidation(StatementInterface paramStatement) throws SQLException {

    }

    @Override
    public String prepareDataValueString(String str) {
        return null;
    }

    @Override
    public boolean open() throws SQLException {
        return false;
    }

    @Override
    public StatementInterface getStatement(String query) throws SQLException {
        return null;
    }

    @Override
    public boolean hasTable(String table) {
        return false;
    }

    @Override
    public boolean truncate(String table) {
        return false;
    }

    @Override
    public String create(DatabaseManager manager, Blueprint blueprint, @Nonnull Map<String, Boolean> options) {
        return setupAndRun(new Create(), blueprint, manager, options);
    }

    @Override
    public String delete(DatabaseManager manager, QueryBuilder query, @Nullable Map<String, Boolean> options) {
        return setupAndRun(new Delete(), query, manager, options);
    }

    @Override
    public String insert(DatabaseManager manager, QueryBuilder query, @Nullable Map<String, Boolean> options) {
        return setupAndRun(new Insert(), query, manager, options);
    }

    @Override
    public String select(DatabaseManager manager, QueryBuilder query, @Nullable Map<String, Boolean> options) {
        return setupAndRun(new Select(), query, manager, options);
    }

    @Override
    public String update(DatabaseManager manager, QueryBuilder query, @Nullable Map<String, Boolean> options) {
        return setupAndRun(new Update(), query, manager, options);
    }
}
