package com.alexkasko.springjdbc.parallel.accessor;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin collection accessor. Copies provided collection into inner immutable one on creation.
 * Element is chosen in circular order ignoring provided parameter.
 * Thread-safe.
 *
 * @author  alexkasko
 * Date: 6/11/12
 * @see DataSourceAccessor
 */
public final class RoundRobinAccessor<T extends JdbcOperations> extends ImmutableAccessor<T, SqlParameterSource> {
    private AtomicInteger index = new AtomicInteger(0);

    /**
     * Protected constructors
     *
     * @param target collection to wrap
     */
    public RoundRobinAccessor(Collection<T> target) {
        super(target);
    }

    /**
     * Generic friendly factory method
     *
     * @param target collection to wrap
     * @param <T> JdbcOperations implementation
     * @return new accessor instance
     */
    public static <T extends JdbcOperations> RoundRobinAccessor<T> of(Collection<T> target) {
        return new RoundRobinAccessor<T>(target);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get(SqlParameterSource params) {
        return delegate.get(incrementAndGet());
    }

    /**
     * Returns current index value
     *
     * @return current index
     */
    public int getIndex() {
        return index.get();
    }

    /**
     * Atomically increments index using target list size modulus
     *
     * @return incremented index
     */
    private int incrementAndGet() {
        for (;;) {
            int current = index.get();
            int next = (current < delegate.size() - 1) ? current + 1 : 0;
            if (index.compareAndSet(current, next)) return current;
        }
    }
}
