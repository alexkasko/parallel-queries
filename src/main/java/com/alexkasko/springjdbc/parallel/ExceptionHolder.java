package com.alexkasko.springjdbc.parallel;

import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Worker exception holder, holds only first setted value, ignores subsequent values.
 * Throws stored exception on demand.
 * Thread-safe.
 *
 * @author alexkasko
 * Date: 6/12/12
 */
class ExceptionHolder {
    private AtomicReference<ParallelQueriesException> target = new AtomicReference<ParallelQueriesException>();

    /**
     * Returns stored exception or null
     *
     * @return stored exception or null
     */
    ParallelQueriesException get() {
        return target.get();
    }

    /**
     * @param target value to set, ignored if value was already set
     */
    void set(ParallelQueriesException target) {
        checkNotNull(target, "Holded value must be non null");
        this.target.compareAndSet(null, target);
    }
}
