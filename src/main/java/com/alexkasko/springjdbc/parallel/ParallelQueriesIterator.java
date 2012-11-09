package com.alexkasko.springjdbc.parallel;

import com.alexkasko.springjdbc.parallel.accessor.DataSourceAccessor;
import com.alexkasko.springjdbc.parallel.accessor.RoundRobinAccessor;
import com.google.common.base.Function;
import com.google.common.collect.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alexkasko.springjdbc.parallel.JdbcTemplateFunction.NPJT_FUNCTION;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.springframework.util.StringUtils.hasText;

/**
 * Executes single SQL query to multiple data sources in parallel using provided executor.
 * Provides data to client as iterator, {@link ArrayBlockingQueue} is used for temporary buffering.
 * Iteration will block awaiting data loaded from sources.
 * Typical usage is to get new instance somewhere (spring prototype bean etc.), provide query params
 * with <code>start</code> method and iterate over until end.
 * Data source exceptions will be propagates as runtime exceptions thrown on 'next()' or 'hasNext()' call.
 * All parallel queries will be cancelled on one query error.
 * Despite using {@code JdbcOperations} instead of {@code NamedParameterJdbcTemplate} iterator takes sql
 * in the same format as {@code NamedParameterJdbcTemplate} does (with {@code :palceholders}) and map declared parameters to
 * provided values using the same methods as {@code NamedParameterJdbcTemplate}.
 * <b>NOT</b> thread-safe (tbd: specify points that break thread safety), instance may be reused calling <code>start</code> method, but only in one thread simultaneously.
 *
 * @author  alexkasko
 * Date: 6/8/12
 * @see ParallelQueriesListener
 * @see com.alexkasko.springjdbc.parallel.accessor.DataSourceAccessor
 */

public class ParallelQueriesIterator<T> extends AbstractIterator<T> {
    private static final Log logger = LogFactory.getLog(ParallelQueriesIterator.class);

    private final Object endOfDataObject = new Object();
    private final FirstValueHolder<RuntimeException> exceptionHolder = new FirstValueHolder<RuntimeException>();

    private final DataSourceAccessor<?, ?> sources;
    private final String sql;
    private final RowMapperFactory<T, ?> mapperFactory;
    private final ExecutorService executor;
    // was made non-generic to allow endOfDataObject
    private final ArrayBlockingQueue<Object> dataQueue;
    private final List<ParallelQueriesListener> listeners = Lists.newArrayList();

    private AtomicBoolean started = new AtomicBoolean(false);
    private AtomicInteger sourcesRemained = new AtomicInteger(0);
    // this field needed anyway in start method, so we'll use it in workers instead of thread.isInterrupted
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Statement> activeStatements = new ConcurrentHashMap<String, Statement>();
    private ImmutableList<Future<?>> futures;

    /**
     * Shortcut constructor, uses {@link RoundRobinAccessor}, new cached thread pool,
     * and {@code JdbcTemplate}s with default settings
     *
     *
     * @param sources list of data sources, will be used in round-robin mode
     * @param sql query to execute using JdbcTemplate
     * @param mapper will be used to get data from result sets
     */
    public ParallelQueriesIterator(Collection<DataSource> sources, String sql, RowMapper<T> mapper) {
        this(RoundRobinAccessor.of(Collections2.transform(sources, NPJT_FUNCTION)),
                sql, Executors.newCachedThreadPool(), mapper, 1024);
    }

    /**
     * Main constructor, uses {@code RowMapper} instead of mapper factory
     *
     * @param sources data sources (wrapped into JDBC templates) accessor
     * @param sql query to execute using JdbcOperations
     * @param executor executor service to run parallel queries into
     * @param mapper will be used to get data from result sets
     * @param bufferSize size of ArrayBlockingQueue data buffer
     */
    public ParallelQueriesIterator(DataSourceAccessor<?, ?> sources,
                                   String sql, ExecutorService executor, RowMapper<T> mapper, int bufferSize) {
        this(sources, sql, executor, SingletoneRowMapperFactory.of(mapper), bufferSize);
    }

    /**
     * Most detailed constructor
     *
     * @param sources data sources (wrapped into JDBC templates) accessor
     * @param sql query to execute using JdbcOperations
     * @param mapperFactory will be used to get data from result sets
     * @param executor executor service to run parallel queries into
     * @param bufferSize size of ArrayBlockingQueue data buffer
     */
    public ParallelQueriesIterator(DataSourceAccessor<?, ?> sources,
                                   String sql, ExecutorService executor, RowMapperFactory<T, ?> mapperFactory, int bufferSize) {
        checkNotNull(sources, "Provided data source accessor is null");
        checkArgument(sources.size() > 0, "No data sources provided");
        checkArgument(hasText(sql), "Provided sql query is blank");
        checkNotNull(executor, "Provided executor is null");
        checkNotNull(mapperFactory, "Provided row mapper factory is null");
        checkArgument(bufferSize > 0, "Buffer size mat be positive, but was: '%s'", bufferSize);
        this.sources = sources;
        this.sql = sql;
        this.mapperFactory = mapperFactory;
        this.executor = executor;
        this.dataQueue = new ArrayBlockingQueue<Object>(bufferSize);
    }

    /**
     * Starts parallel query execution in data sources. May be called multiple times to reuse iterator instance,
     * Must be called only on exhausted iterators (which have all worker threads finished).
     * May not be called on cancelled iterators.
     *
     * @param params query params
     * @return iterator itself
     */
    public ParallelQueriesIterator<T> start(Collection<? extends SqlParameterSource> params) {
        checkNotNull(params, "Provided parameters collection is null");
        checkArgument(params.size() > 0, "Provided collection is empty");
        checkState(!cancelled.get(), "This iterator is cancelled and cannot be restarted");
        this.dataQueue.clear();
        this.sourcesRemained.set(params.size());
        this.futures = ImmutableList.copyOf(Collections2.transform(params, new SubmitFun()));
        this.started.set(true);
        return this;
    }

    /**
     * Cancels queries processing in all sources, by setting cancelled state, then cancelling all active
     * prepared statements (for workers blocked on query completion) and then interrupting
     * all threads (for workers blocked on result queue).
     * May be called from another thread. Subsequent calls do nothing.
     *
     * @return count of threads that were actually interrupted in processing
     */
    public int cancel() {
        if(!started.get()) return 0;
        // set cancelled state
        boolean wasCancelled = cancelled.getAndSet(true);
        if(wasCancelled) return 0;
        int res = 0;
        // cancel statements
        for(Statement st : activeStatements.values()) {
            cancelStatement(st);
        }
        // cancel threads
        for(Future<?> fu : futures) {
            if(fu.cancel(true)) res += 1;
        }
        return res;
    }

    /**
     * Register listener for "query success" and "query error" events
     *
     * @param listener data source query events will be reported to this listener
     * @return iterator itself
     */
    public ParallelQueriesIterator<T> addListener(ParallelQueriesListener listener) {
        this.listeners.add(listener);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ParallelQueriesIterator");
        sb.append("{endOfDataObject=").append(endOfDataObject);
        sb.append(", sources=").append(sources);
        sb.append(", sql='").append(sql).append('\'');
        sb.append(", mapperFactory=").append(mapperFactory);
        sb.append(", executor=").append(executor);
        sb.append(", started=").append(started);
        sb.append(", sourcesRemained=").append(sourcesRemained);
        sb.append(", cancelled=").append(cancelled);
        sb.append('}');
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    protected T computeNext() {
        checkState(started.get(), "Iterator wasn't started, call 'start' method first");
        Object ob;
        while(endOfDataObject == (ob = takeData())) {
            if(0 == sourcesRemained.decrementAndGet()) return endOfData();
        }
        if(exceptionHolder == ob) {
            cancel();
            throw exceptionHolder.get();
        }
        return (T) ob;
    }

    private void putData(Object data) {
        try {
            dataQueue.put(data);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParallelQueriesException(e);
        }
    }

    private Object takeData() {
        try {
            return dataQueue.take();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParallelQueriesException(e);
        }
    }

    private void cancelStatement(Statement stmt) {
        try {
            stmt.cancel();
        } catch(Exception e) { // driver may throw different exceptions
            logger.warn("Exception thrown on statement cancelling: '" + stmt + "'", e);
        }
    }

    private class Worker implements Runnable {
        private final JdbcOperations jo;
        private final SqlParameterSource params;

        private Worker(JdbcOperations jo, SqlParameterSource params) {
            this.jo = jo;
            this.params = params;
        }

        @Override
        @SuppressWarnings("unchecked") // workaround to
        // keep RowMapperFactory fully typed without additional
        // generic arguments to iterator itself
        public void run() {
            String registryKey = Thread.currentThread().getName();
            try {
                RowMapperFactory ungeneric = mapperFactory;
                RowMapper<T> mapper = ungeneric.produce(params);
                Extractor extractor = new Extractor(mapper);
                // sql parameters processing is the same as in NamedParameterJdbcTemplate
                PreparedStatementCreator psc = new CancellableStatementCreator(registryKey, activeStatements, sql, params);
                jo.query(psc, extractor);
                for(ParallelQueriesListener li : listeners) li.success(jo, sql, params);
            } catch (Throwable e) { // we do not believe to JDBC drivers' error reporting
                exceptionHolder.set(new ParallelQueriesException(e));
                putData(exceptionHolder);
                for(ParallelQueriesListener li : listeners) li.error(jo, sql, params, e);
            } finally {
                activeStatements.remove(registryKey);
            }
        }
    }

    private class Extractor implements ResultSetExtractor<Void> {
        private final RowMapper<T> mapper;

        private Extractor(RowMapper<T> mapper) {
            this.mapper = mapper;
        }

        @Override
        public Void extractData(ResultSet rs) throws SQLException, DataAccessException {
            try {
                int rowNum = 0;
                while(rs.next()) {
                    if(cancelled.get()) throw new RuntimeException("Execution was cancelled");
                    Object obj = mapper.mapRow(rs, rowNum++);
                    dataQueue.put(obj);
                }
                if(cancelled.get()) throw new RuntimeException("Execution was cancelled");
                dataQueue.put(endOfDataObject);
            } catch(Throwable e) { // we do not believe to JDBC drivers' error reporting
                exceptionHolder.set(new ParallelQueriesException(e));
                putData(exceptionHolder);
            }
            return null;
        }
    }

    private class SubmitFun implements Function<SqlParameterSource, Future<?>> {
        @Override
        @SuppressWarnings("unchecked") // workaround to
        // keep accessors fully typed without additional
        // generic arguments to iterator itself
        public Future<?> apply(SqlParameterSource params) {
            DataSourceAccessor ungeneric = sources;
            JdbcOperations jo = (JdbcOperations) ungeneric.get(params);
            Worker worker = new Worker(jo, params);
            return executor.submit(worker);
        }
    }
}
