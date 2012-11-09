package com.alexkasko.springjdbc.parallel;

import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reference holder, holds only first setted value, ignores subsequent values.
 * Thread-safe.
 *
 * @author alexkasko
 * Date: 6/12/12
 */
class FirstValueHolder<T> {
    private AtomicReference<T> target = new AtomicReference<T>();

    /**
     * @return holded value or null if no value set
     */
    T get() {
        return target.get();
    }

    /**
     * @param target value to set, ignored if value was already set
     */
    void set(T target) {
        checkNotNull(target, "Holded value must be non null");
        this.target.compareAndSet(null, target);
    }
}
