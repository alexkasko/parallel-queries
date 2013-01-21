package com.alexkasko.springjdbc.parallel;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Implementors must be registered on iterator using {@link ParallelQueriesIterator#addListener(ParallelQueriesListener)}
 * method. They will be called on every successful and every errored query from different worker threads.
 * Must be thread-safe.
 *
 * @author  alexkasko
 * Date: 6/16/12
 * @see ParallelQueriesIterator
 */
public interface ParallelQueriesListener {
    /**
     * @param npjo wrapper for data source, that was used to execute query
     * @param sql SQL query
     * @param params query input parameters
     */
    void success(NamedParameterJdbcOperations npjo, String sql, SqlParameterSource params);

    /**
     * @param npjo wrapper for data source, that was used to execute query
     * @param sql SQL query
     * @param params query input parameters
     * @param ex exception from JDBC
     */
    void error(NamedParameterJdbcOperations npjo, String sql, SqlParameterSource params, Throwable ex);
}
