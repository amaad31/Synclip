package dev.synclip;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Buffers file change events while the device is offline.
 * When the connection is restored, all queued events are flushed
 * and processed in order.
 *
 * Thread-safe — uses ConcurrentLinkedQueue internally.
 */
public class OfflineQueue {

    /**
     * Represents a buffered file system event.
     */
    public record QueuedEvent(
            FileWatcher.Event type,
            Path path,
            Instant timestamp
    ) {}

    private final ConcurrentLinkedQueue<QueuedEvent> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean online = false;

    public OfflineQueue(boolean initiallyOnline) {
        this.online = initiallyOnline;
    }

    /**
     * Enqueues a file event if offline.
     * If online, returns false — caller should process immediately.
     */
    public boolean enqueue(FileWatcher.Event type, Path path) {
        if (online) return false;
        queue.add(new QueuedEvent(type, path, Instant.now()));
        return true;
    }

    /**
     * Marks the device as online and returns all queued events.
     * Clears the queue after returning.
     */
    public List<QueuedEvent> flush() {
        online = true;
        List<QueuedEvent> events = new ArrayList<>(queue);
        queue.clear();
        return events;
    }

    /**
     * Marks the device as offline — subsequent events will be queued.
     */
    public void goOffline() {
        online = false;
    }

    /**
     * Marks the device as online without flushing.
     */
    public void goOnline() {
        online = true;
    }

    public boolean isOnline()  { return online; }
    public int size()          { return queue.size(); }
    public boolean isEmpty()   { return queue.isEmpty(); }
}