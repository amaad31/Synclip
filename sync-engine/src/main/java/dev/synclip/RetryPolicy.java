package dev.synclip;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Retries a failing operation up to maxAttempts times with exponential backoff.
 *
 * Example:
 *   RetryPolicy retry = new RetryPolicy(3, 100);
 *   retry.execute(() -> uploadChunk(chunk));
 *
 * Attempt 1 fails → wait 100ms
 * Attempt 2 fails → wait 200ms
 * Attempt 3 fails → throw exception
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialDelayMs;

    public RetryPolicy(int maxAttempts, long initialDelayMs) {
        if (maxAttempts < 1)
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (initialDelayMs < 0)
            throw new IllegalArgumentException("initialDelayMs must be >= 0");
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    /**
     * Executes the given operation, retrying on IOException.
     * Returns the result if successful, throws after maxAttempts failures.
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        Exception lastException = null;
        long delay = initialDelayMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.call();
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    Thread.sleep(delay);
                    delay *= 2; // exponential backoff: 100ms → 200ms → 400ms
                }
            }
        }

        throw new IOException(
                "Upload failed after " + maxAttempts + " attempts", lastException);
    }

    /** Convenience overload for void operations. */
    public void execute(RunnableWithException operation) throws Exception {
        execute(() -> { operation.run(); return null; });
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws Exception;
    }

    public int getMaxAttempts() { return maxAttempts; }
    public long getInitialDelayMs() { return initialDelayMs; }
}