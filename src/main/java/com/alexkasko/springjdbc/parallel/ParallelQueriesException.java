package com.alexkasko.springjdbc.parallel;

import org.springframework.dao.DataAccessException;

import static java.lang.Thread.currentThread;

/**
 * Exception for errors in async query workers
 *
 * @author alexkasko
 * Date: 6/12/12
 * @see ParallelQueriesIterator
 */
class ParallelQueriesException extends DataAccessException {
    public ParallelQueriesException(Throwable cause) {
        super("Thread: '" + currentThread().getName() + "', message: '" + cause.getMessage() + "'", cause);
    }
}
