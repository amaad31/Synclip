package dev.synclip;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    // -------------------------------------------------------------------------
    // Successful execution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("successful operation executes once")
    void successfulOperationExecutedOnce() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(3, 0);

        retry.execute(() -> count.incrementAndGet());

        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("returns the operation result")
    void returnsResultOfOperation() throws Exception {
        RetryPolicy retry = new RetryPolicy(3, 0);
        String result = retry.execute(() -> "hello synclip");
        assertEquals("hello synclip", result);
    }

    // -------------------------------------------------------------------------
    // Retry Logik
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("retries after IOException and succeeds on second attempt")
    void retriesAfterIOException() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(3, 0);

        retry.execute(() -> {
            if (count.incrementAndGet() < 2)
                throw new IOException("Netzwerk weg");
        });

        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("versucht genau maxAttempts mal bevor es aufgibt")
    void attemptsExactlyMaxTimes() {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(3, 0);

        assertThrows(IOException.class, () ->
            retry.execute(() -> {
                count.incrementAndGet();
                throw new IOException("immer fehlschlagend");
            })
        );

        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("throws IOException after all attempts fail")
    void throwsAfterAllAttemptsFail() {
        RetryPolicy retry = new RetryPolicy(3, 0);

        IOException ex = assertThrows(IOException.class, () ->
            retry.execute(() -> { throw new IOException("Timeout"); })
        );

        assertTrue(ex.getMessage().contains("3 attempts"));
    }

    @Test
    @DisplayName("does not retry on non-IOException and throws immediately")
    void doesNotRetryOnNonIOException() {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(3, 0);

        assertThrows(RuntimeException.class, () ->
            retry.execute(() -> {
                count.incrementAndGet();
                throw new RuntimeException("kein Netzwerkfehler");
            })
        );

        // Only 1 attempt, no retry on RuntimeException
        assertEquals(1, count.get());
    }

    // -------------------------------------------------------------------------
    // Exponential Backoff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("exponential backoff increases delay on each retry")
    void exponentialBackoffIncreasesDelay() {
        AtomicInteger count = new AtomicInteger(0);
        long[] timestamps = new long[3];
        RetryPolicy retry = new RetryPolicy(3, 50); // start 50ms

        assertThrows(IOException.class, () ->
            retry.execute(() -> {
                int i = count.getAndIncrement();
                timestamps[i] = System.currentTimeMillis();
                throw new IOException("fail");
            })
        );

        long delay1 = timestamps[1] - timestamps[0]; // ~50ms
        long delay2 = timestamps[2] - timestamps[1]; // ~100ms

        assertTrue(delay2 > delay1,
                "Zweite Wartezeit (" + delay2 + "ms) muss länger sein als erste (" + delay1 + "ms)");
    }

    // -------------------------------------------------------------------------
    // Edge Cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("maxAttempts = 1 means no retry")
    void maxAttemptsOneNoRetry() {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(1, 0);

        assertThrows(IOException.class, () ->
            retry.execute(() -> {
                count.incrementAndGet();
                throw new IOException("fail");
            })
        );

        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("constructor rejects maxAttempts < 1")
    void constructorRejectsZeroAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, 100));
    }

    @Test
    @DisplayName("constructor rejects negative delay")
    void constructorRejectsNegativeDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(3, -1));
    }
}