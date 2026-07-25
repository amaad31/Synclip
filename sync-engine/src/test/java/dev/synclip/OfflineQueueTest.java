package dev.synclip;

import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OfflineQueueTest {

    @Test
    @DisplayName("enqueue returns false when online  event should be processed immediately")
    void enqueueReturnsFalseWhenOnline() {
        OfflineQueue queue = new OfflineQueue(true);
        boolean queued = queue.enqueue(FileWatcher.Event.CREATED, Path.of("file.txt"));
        assertFalse(queued);
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("enqueue returns true and stores event when offline")
    void enqueueReturnsTrueWhenOffline() {
        OfflineQueue queue = new OfflineQueue(false);
        boolean queued = queue.enqueue(FileWatcher.Event.CREATED, Path.of("file.txt"));
        assertTrue(queued);
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("multiple events are queued in order when offline")
    void multipleEventsQueuedInOrder() {
        OfflineQueue queue = new OfflineQueue(false);
        queue.enqueue(FileWatcher.Event.CREATED,  Path.of("a.txt"));
        queue.enqueue(FileWatcher.Event.MODIFIED, Path.of("b.txt"));
        queue.enqueue(FileWatcher.Event.DELETED,  Path.of("c.txt"));
        assertEquals(3, queue.size());
    }

    @Test
    @DisplayName("flush returns all queued events and clears the queue")
    void flushReturnsAllEventsAndClearsQueue() {
        OfflineQueue queue = new OfflineQueue(false);
        queue.enqueue(FileWatcher.Event.CREATED,  Path.of("a.txt"));
        queue.enqueue(FileWatcher.Event.MODIFIED, Path.of("b.txt"));

        List<OfflineQueue.QueuedEvent> events = queue.flush();

        assertEquals(2, events.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("flush sets device to online")
    void flushSetsDeviceOnline() {
        OfflineQueue queue = new OfflineQueue(false);
        assertFalse(queue.isOnline());
        queue.flush();
        assertTrue(queue.isOnline());
    }

    @Test
    @DisplayName("goOffline stops immediate processing")
    void goOfflineStopsImmediateProcessing() {
        OfflineQueue queue = new OfflineQueue(true);
        queue.goOffline();
        boolean queued = queue.enqueue(FileWatcher.Event.CREATED, Path.of("file.txt"));
        assertTrue(queued);
    }

    @Test
    @DisplayName("goOnline + enqueue returns false  back to immediate processing")
    void goOnlineResumesImmediateProcessing() {
        OfflineQueue queue = new OfflineQueue(false);
        queue.goOnline();
        boolean queued = queue.enqueue(FileWatcher.Event.CREATED, Path.of("file.txt"));
        assertFalse(queued);
    }

    @Test
    @DisplayName("flushed events contain correct type and path")
    void flushedEventsContainCorrectData() {
        OfflineQueue queue = new OfflineQueue(false);
        queue.enqueue(FileWatcher.Event.MODIFIED, Path.of("doc.txt"));

        OfflineQueue.QueuedEvent event = queue.flush().get(0);

        assertEquals(FileWatcher.Event.MODIFIED, event.type());
        assertEquals(Path.of("doc.txt"),          event.path());
        assertNotNull(event.timestamp());
    }

    @Test
    @DisplayName("flush on empty queue returns empty list")
    void flushOnEmptyQueueReturnsEmptyList() {
        OfflineQueue queue = new OfflineQueue(false);
        List<OfflineQueue.QueuedEvent> events = queue.flush();
        assertTrue(events.isEmpty());
    }
}