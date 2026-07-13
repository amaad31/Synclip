package dev.synclip;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens for a programmatic trigger and executes a callback.
 *
 * Trigger options:
 *   1. trigger()  — programmatic, used by tests and integrations
 *   2. CLI        — future extension via JNativeHook for Ctrl+Shift+V
 */
public class HotkeyListener implements AutoCloseable {

    private final Runnable onTrigger;
    private final ExecutorService executor;
    private volatile boolean running = false;

    public HotkeyListener(Runnable onTrigger) {
        if (onTrigger == null)
            throw new IllegalArgumentException("onTrigger must not be null");
        this.onTrigger = onTrigger;
        this.executor  = Executors.newSingleThreadExecutor();
    }

    /** Starts the listener. */
    public void start() {
        if (running) return;
        running = true;
    }

    /**
     * Programmatic trigger — simulates the user pressing the hotkey.
     * Executes the callback on the calling thread.
     */
    public void trigger() {
        if (!running)
            throw new IllegalStateException("HotkeyListener is not running");
        onTrigger.run();
    }

    /**
     * Async trigger — executes the callback on a background thread.
     * Useful when trigger is called from a UI event handler.
     */
    public void triggerAsync() {
        if (!running)
            throw new IllegalStateException("HotkeyListener is not running");
        executor.submit(onTrigger);
    }

    public boolean isRunning() { return running; }

    @Override
    public void close() {
        running = false;
        executor.shutdown();
    }
}