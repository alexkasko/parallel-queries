package com.alexkasko.springjdbc.parallel;

import com.alexkasko.springjdbc.parallel.accessor.RoundRobinNpjtAccessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang.RandomStringUtils.randomAscii;
import static org.junit.Assert.assertEquals;

/**
 * User: alexkasko
 * Date: 6/12/12
 */
public class ParallelQueriesIteratorTest {

    @Test
    public void test() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jt = new NamedParameterJdbcTemplate(ds);
        jt.getJdbcOperations().execute("create table foo(bar varchar(42))");
        jt.getJdbcOperations().update("insert into foo(bar) values('41')");
        jt.getJdbcOperations().update("insert into foo(bar) values('42')");
        jt.getJdbcOperations().update("insert into foo(bar) values('43')");
        RoundRobinNpjtAccessor robin = new RoundRobinNpjtAccessor(jt);
        Collection<MapSqlParameterSource> params = ImmutableList.of(new MapSqlParameterSource(ImmutableMap.of("val", 40)));
        // single thread used, buffer must me bigger than data
        ExecutorService sameThreadExecutor = MoreExecutors.sameThreadExecutor();
        ParallelQueriesIterator<String> iter = new ParallelQueriesIterator<String>(robin,
                "select bar from foo where bar > :val", sameThreadExecutor, new SimpleMapper(), 10)
                .start(params);
        assertEquals("41", iter.next());
        assertEquals("42", iter.next());
        assertEquals("43", iter.next());
        // check restart
        iter.start(params);
        assertEquals("41", iter.next());
        assertEquals("42", iter.next());
        assertEquals("43", iter.next());
    }

    // set this to upper value for debugging multithreaded test
    private static final int WORKER_QUERY_DELAY_MILLIS = 1;

    /**
     * Multithreaded stress test, commented deliberately
     */
//    @Test
    public void testStress() {
        { // single thread
            NamedParameterJdbcTemplate jt = createJT();
            long start = currentTimeMillis();
            long res = jt.getJdbcOperations().query("select bar from foo", new Extractor());
//            1300
            System.out.println("10000 records from one thread: " + (currentTimeMillis() - start));
            assertEquals(res, 10000);
        }
        { // 20 threads
            int count = 20;
            ImmutableList.Builder<NamedParameterJdbcTemplate> builder = ImmutableList.builder();
            for(int i = 0; i < count; i++) builder.add(createJT());
            RoundRobinNpjtAccessor robin = new RoundRobinNpjtAccessor(builder.build());
            long start = currentTimeMillis();
            ParallelQueriesIterator<String> iter = new ParallelQueriesIterator<String>(robin, "select bar from foo",
                    Executors.newCachedThreadPool(), new SlowpokeMapper(), 100)
                    .start(params(count));
            int resCount = 0;
            while (iter.hasNext()) {
                iter.next();
                resCount += 1;
            }
//          2100
            System.out.println("200000 records from 20 threads: " + (currentTimeMillis() - start));
            assertEquals(resCount, 10000 * count);
        }
    }

    private NamedParameterJdbcTemplate createJT() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:" + randomAlphanumeric(10) + ";DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jt = new NamedParameterJdbcTemplate(ds);
        jt.getJdbcOperations().execute("create table foo(bar varchar(42))");
        for(int i=0; i< 10000; i++) {
            jt.update("insert into foo(bar) values(:str)", ImmutableMap.of("str", randomAscii(42)));
        }
        return jt;
    }

    private class Extractor implements ResultSetExtractor<Long> {
        @Override
        public Long extractData(ResultSet rs) throws SQLException, DataAccessException {
            SlowpokeMapper mapper = new SlowpokeMapper();
            long count = 0;
            while (rs.next()) {
                mapper.mapRow(rs, -1);
                count += 1;
            }
            return count;
        }
    }

    private class SimpleMapper implements RowMapper<String> {
        @Override
        public String mapRow(ResultSet rs, int rowNum) throws SQLException {
            return rs.getString("bar");
        }
    }

    private class SlowpokeMapper implements RowMapper<String> {
        AtomicInteger count = new AtomicInteger(0);

        @Override
        public String mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                int co = count.incrementAndGet();
                if(0 == co % 10) Thread.sleep(WORKER_QUERY_DELAY_MILLIS);
                return rs.getString("bar");
            } catch(InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<? extends SqlParameterSource> params(int count) {
        ImmutableList.Builder<MapSqlParameterSource> builder = ImmutableList.builder();
        for(int i = 0; i < count; i++) {
            builder.add(new MapSqlParameterSource());
        }
        return builder.build();
    }
}
