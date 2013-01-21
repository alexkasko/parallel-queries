package com.alexkasko.springjdbc.parallel.accessor;

import com.google.common.collect.ForwardingCollection;
import com.google.common.collect.ImmutableList;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract accessor implementation as a wrapper around provided collection.
 * Copies provided collection into inner immutable one on creation. Thread-safe.
 *
 * @author alexkasko
 * Date: 6/11/12
 * @see DataSourceAccessor
 */
public abstract class ImmutableAccessor<T extends NamedParameterJdbcOperations, P extends SqlParameterSource>
        extends ForwardingCollection<T> implements DataSourceAccessor<T, P> {
    protected final ImmutableList<T> delegate;

    /**
     * Protected constructor, copies provided collection into inner immutable one
     *
     * @param collection collection to wrap
     */
    protected ImmutableAccessor(Collection<T> collection) {
        checkNotNull(collection, "Provided collection must be not null");
        checkArgument(collection.size() > 0, "Provided collection must be not empty");
        this.delegate = ImmutableList.copyOf(collection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<T> delegate() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ImmutableAccessor");
        sb.append("{delegate=").append(delegate);
        sb.append('}');
        return sb.toString();
    }
}
