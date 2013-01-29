package com.alexkasko.springjdbc.parallel;

/**
 * Exception handler for exceptions propagated from worker threads to caller thread.
 *
 * @author alexkasko
 * Date: 1/29/13
 */
public interface ParallelQueriesExceptionHandler {
    /**
     * If implementation rethrow exception (of throw another exception) workers execution
      * will be cancelled and exception will be propagated to {@link java.util.Iterator#next()}
      * or {@link java.util.Iterator#hasNext()} call.
      * Will be called from the iterator caller thread on worker exception.
     *
     * @param e worker exception wrapped into {@link ParallelQueriesException}
     * @throws RuntimeException rethrow or throw new exception to cancel workers execution
     */
    void handle(ParallelQueriesException e) throws RuntimeException;
}
