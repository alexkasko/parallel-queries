package com.alexkasko.springjdbc.parallel;

import com.google.common.base.Function;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper function, wraps {@link javax.sql.DataSource} into {@code NamedParameterJdbcTemplate}
 *
 * @author alexkasko
 * Date: 11/9/12
 */
class JdbcTemplateFunction implements Function<DataSource, JdbcTemplate> {
    static final Function<DataSource, JdbcTemplate> NPJT_FUNCTION = new JdbcTemplateFunction();

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcTemplate apply(DataSource input) {
        checkNotNull(input, "Provided data source is null");
        return new JdbcTemplate(input);
    }
}
