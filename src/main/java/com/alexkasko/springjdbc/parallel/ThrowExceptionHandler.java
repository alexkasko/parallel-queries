package com.alexkasko.springjdbc.parallel;

/**
 * Rethrows {@link ParallelQueriesException} untouched
 *
 * @author alexkasko
 * Date: 1/29/13
 */
class ThrowExceptionHandler implements ParallelQueriesExceptionHandler {
    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(ParallelQueriesException e) throws ParallelQueriesException {
        throw e;
    }
}
