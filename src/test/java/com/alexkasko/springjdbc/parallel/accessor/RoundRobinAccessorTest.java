package com.alexkasko.springjdbc.parallel.accessor;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.assertEquals;

/**
 * User: alexkasko
 * Date: 6/11/12
 */
public class RoundRobinAccessorTest {

    @Test
    public void test() {
        JdbcTemplate jt1 = new JdbcTemplate();
        JdbcTemplate jt2 = new JdbcTemplate();
        JdbcTemplate jt3 = new JdbcTemplate();
        RoundRobinAccessor accessor = RoundRobinAccessor.of(ImmutableList.of(jt1, jt2, jt3));
        assertEquals(jt1, accessor.get(null));
        assertEquals(jt2, accessor.get(null));
        assertEquals(jt3, accessor.get(null));
        assertEquals(jt1, accessor.get(null));
        assertEquals(jt2, accessor.get(null));
        assertEquals(jt3, accessor.get(null));
        assertEquals(jt1, accessor.get(null));
    }
}
