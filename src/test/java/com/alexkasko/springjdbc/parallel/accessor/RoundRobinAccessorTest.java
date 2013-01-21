package com.alexkasko.springjdbc.parallel.accessor;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.Assert.assertEquals;

/**
 * User: alexkasko
 * Date: 6/11/12
 */
public class RoundRobinAccessorTest {

    @Test
    public void test() {
        JdbcTemplate jt = new JdbcTemplate();
        NamedParameterJdbcTemplate jt1 = new NamedParameterJdbcTemplate(jt);
        NamedParameterJdbcTemplate jt2 = new NamedParameterJdbcTemplate(jt);
        NamedParameterJdbcTemplate jt3 = new NamedParameterJdbcTemplate(jt);
        RoundRobinNpjtAccessor accessor = new RoundRobinNpjtAccessor(jt1, jt2, jt3);
        assertEquals(jt1, accessor.get(null));
        assertEquals(jt2, accessor.get(null));
        assertEquals(jt3, accessor.get(null));
        assertEquals(jt1, accessor.get(null));
        assertEquals(jt2, accessor.get(null));
        assertEquals(jt3, accessor.get(null));
        assertEquals(jt1, accessor.get(null));
    }
}
