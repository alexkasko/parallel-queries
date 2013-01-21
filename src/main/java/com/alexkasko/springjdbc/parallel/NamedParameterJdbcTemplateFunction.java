package com.alexkasko.springjdbc.parallel;

import com.google.common.base.Function;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper function, wraps {@link javax.sql.DataSource} into {@code NamedParameterJdbcTemplate}
 *
 * @author alexkasko
 * Date: 11/9/12
 */
class NamedParameterJdbcTemplateFunction implements Function<DataSource, NamedParameterJdbcOperations> {
    static final Function<DataSource, NamedParameterJdbcOperations> NPJT_FUNCTION = new NamedParameterJdbcTemplateFunction();

    /**
     * {@inheritDoc}
     */
    @Override
    public NamedParameterJdbcTemplate apply(DataSource input) {
        checkNotNull(input, "Provided data source is null");
        return new NamedParameterJdbcTemplate(input);
    }
}
