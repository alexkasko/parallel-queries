package com.alexkasko.springjdbc.parallel;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Spring's row mappers haven't access to query params.
 * This class may be used if such access is needed on result set mapping.
 *
 * @author alexkasko
 * Date: 8/18/12
 * @see ParallelQueriesIterator
 */
public interface RowMapperFactory<T, P extends SqlParameterSource> {
    /**
     * Must produce row mapper. Will be called by {@link ParallelQueriesIterator} before every SQL query
     *
     * @param params query params
     * @return row mapper for query results
     */
    RowMapper<T> produce(P params);
}
