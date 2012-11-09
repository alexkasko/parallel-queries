package com.alexkasko.springjdbc.parallel.accessor;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.Collection;

/**
 * Read-only collection interface.
 *
 * @author alexkasko
 * Date: 6/11/12
 * @see RoundRobinAccessor
 */
public interface DataSourceAccessor<T extends JdbcOperations, P extends SqlParameterSource> extends Collection<T> {
    /**
     *
     *
     * @return collection element
     */

    /**
     * Method to access collection element.
     * Element is chosen by implementation.
     *
     * @param params may be used by implementation for choosing
     * @return collection element
     */
    T get(P params);
}
