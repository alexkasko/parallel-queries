package com.alexkasko.springjdbc.parallel.accessor;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collection;

import static java.util.Arrays.asList;

/**
 * {@link RoundRobinAccessor} subclass for {@code NamedParameterJdbcTemplate}.
 *
 * @author alexkasko
 * Date: 1/21/13
 * @since 1.1
 * @see DataSourceAccessor
 */
public class RoundRobinNpjtAccessor extends RoundRobinAccessor<NamedParameterJdbcTemplate> {

    /**
     * Vararg constructor
     *
     * @param jts data sources
     */
    public RoundRobinNpjtAccessor(NamedParameterJdbcTemplate... jts) {
        this(asList(jts));
    }

    /**
     * Main constructor
     *
     * @param target data sources
     */
    public RoundRobinNpjtAccessor(Collection<NamedParameterJdbcTemplate> target) {
        super(target);
    }
}
