package dev.synclip;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    // -------------------------------------------------------------------------
    // Erfolgreiche Ausführung
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("erfolgreiche Operation wird einmal ausgeführt")
    void successfulOperationExecutedOnce() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(3, 0);

        retry.execute(() -> count.incrementAndGet());

        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("gibt den Rückgabewert der Operation zurück")
    void returnsResultOfOperation() throws Exception {
        RetryPolicy retry = new RetryPolicy(3, 0);
        String result = retry.execute(() -> "hello synclip");
        assertEquals("hello synclip", result);
    }

    // -------------------------------------------------------------------------
    // Retry Logik
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("wiederholt nach IOException — erfolgreich beim zweiten Versuch")
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
    @DisplayName("wirft IOException nach allen fehlgeschlagenen Versuchen")
    void throwsAfterAllAttemptsFail() {
        RetryPolicy retry = new RetryPolicy(3, 0);

        IOException ex = assertThrows(IOException.class, () ->
            retry.execute(() -> { throw new IOException("Timeout"); })
        );

        assertTrue(ex.getMessage().contains("3 attempts"));
    }

    @Test
    @DisplayName("gibt nicht auf bei nicht-IOException — wirft sofort")
    void doesNotRetryOnNonIOException() {
        AtomicInteger count = new AtomicInteger(0);
        RetryPolicy retry = new RetryPolicy(3, 0);

        assertThrows(RuntimeException.class, () ->
            retry.execute(() -> {
                count.incrementAndGet();
                throw new RuntimeException("kein Netzwerkfehler");
            })
        );

        // Nur 1 Versuch — kein Retry bei RuntimeException
        assertEquals(1, count.get());
    }

    // -------------------------------------------------------------------------
    // Exponential Backoff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("exponential backoff — wartet länger bei jedem Versuch")
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
    @DisplayName("maxAttempts = 1 bedeutet kein Retry")
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
    @DisplayName("Konstruktor lehnt maxAttempts < 1 ab")
    void constructorRejectsZeroAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, 100));
    }

    @Test
    @DisplayName("Konstruktor lehnt negativen Delay ab")
    void constructorRejectsNegativeDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(3, -1));
    }
}