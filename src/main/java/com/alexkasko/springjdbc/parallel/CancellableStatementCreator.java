package com.alexkasko.springjdbc.parallel;

import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue;
import static org.springframework.jdbc.core.namedparam.NamedParameterUtils.*;
import static org.springframework.util.StringUtils.hasText;

/**
 * {@code PreparedStatementCreator} implementation, that places created {@link PreparedStatement}
 * into provided registry for possible cancellation from other thread.
 * Other thread must check atomic reference payload for nullability.
 * Sql parameters processing is the same as in {@code NamedParameterJdbcTemplate}
 * Each instance should be used only once.
 *
 * @author alexkasko
 * Date: 11/6/12
 */
class CancellableStatementCreator implements PreparedStatementCreator {
    private final String registryKey;
    private final Map<String, Statement> registry;
    private final String sql;
    private final SqlParameterSource paramSource;

    /**
     * Main constructor
     *
     * @param registryKey registry key for created statement
     * @param registry statement registry
     * @param sql sql string with spring-jdbc named parameters
     * @param params parameter source
     */
    CancellableStatementCreator(String registryKey, Map<String, Statement> registry, String sql, SqlParameterSource params) {
        checkArgument(hasText(registryKey), "Provided registry key is blank");
        checkNotNull(registry, "Provided registry is null");
        checkArgument(hasText(sql), "Provided sql is blank");
        checkNotNull(params, "Provided sql parameter source is null");
        this.registryKey = registryKey;
        this.registry = registry;
        this.sql = sql;
        this.paramSource = params;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
        // substitute named params,
        // see NamedParameterJdbcTemplate#getPreparedStatementCreator(String sql, SqlParameterSource paramSource)
        ParsedSql parsedSql = parseSqlStatement(sql);
        String sqlToUse = NamedParameterUtils.substituteNamedParameters(parsedSql, paramSource);
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql, paramSource, null);
        List<SqlParameter> declaredParameters = NamedParameterUtils.buildSqlParameterList(parsedSql, paramSource);
        PreparedStatementCreatorFactory pscf = new PreparedStatementCreatorFactory(sqlToUse, declaredParameters);
        PreparedStatementCreator psc = pscf.newPreparedStatementCreator(params);
        PreparedStatement stmt = psc.createPreparedStatement(con);
        registry.put(registryKey, stmt);
        return stmt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CancellableStatementCreator");
        sb.append("{registryKey='").append(registryKey).append('\'');
        sb.append(", sql='").append(sql).append('\'');
        sb.append(", paramSource=").append(paramSource);
        sb.append('}');
        return sb.toString();
    }
}