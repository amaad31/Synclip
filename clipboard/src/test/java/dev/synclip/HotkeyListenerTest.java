package dev.synclip;

import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HotkeyListenerTest {

    private HotkeyListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) listener.close();
    }

    @Test
    @DisplayName("start() sets isRunning to true")
    void startSetsIsRunningTrue() {
        listener = new HotkeyListener(() -> {});
        listener.start();
        assertTrue(listener.isRunning());
    }

    @Test
    @DisplayName("close() sets isRunning to false")
    void closeSetsIsRunningFalse() throws Exception {
        listener = new HotkeyListener(() -> {});
        listener.start();
        listener.close();
        Thread.sleep(100);
        assertFalse(listener.isRunning());
    }

    @Test
    @DisplayName("start() is idempotent")
    void startIsIdempotent() {
        listener = new HotkeyListener(() -> {});
        listener.start();
        listener.start();
        assertTrue(listener.isRunning());
    }

    @Test
    @DisplayName("trigger() calls onTrigger callback")
    void triggerCallsCallback() {
        AtomicInteger count = new AtomicInteger(0);
        listener = new HotkeyListener(count::incrementAndGet);
        listener.start();

        listener.trigger();
        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("trigger() can be called multiple times")
    void triggerCanBeCalledMultipleTimes() {
        AtomicInteger count = new AtomicInteger(0);
        listener = new HotkeyListener(count::incrementAndGet);
        listener.start();

        listener.trigger();
        listener.trigger();
        listener.trigger();

        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("trigger() before start() throws IllegalStateException")
    void triggerBeforeStartThrows() {
        listener = new HotkeyListener(() -> {});
        assertThrows(IllegalStateException.class, () -> listener.trigger());
    }

    @Test
    @DisplayName("constructor rejects null callback")
    void constructorRejectsNullCallback() {
        assertThrows(IllegalArgumentException.class,
                () -> new HotkeyListener(null));
    }

    @Test
    @DisplayName("trigger() executes callback on same thread")
    void triggerExecutesOnCallingThread() {
        String[] threadName = new String[1];
        listener = new HotkeyListener(() ->
                threadName[0] = Thread.currentThread().getName());
        listener.start();

        listener.trigger();

        assertEquals(Thread.currentThread().getName(), threadName[0]);
    }
}