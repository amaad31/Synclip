package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class FileWatcherTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("detects file creation")
    void detectsFileCreation() throws Exception {
        List<Path> created = new CopyOnWriteArrayList<>();
        FileWatcher watcher = new FileWatcher(tempDir,
                (e, p) -> { if (e == FileWatcher.Event.CREATED) created.add(p); });
        watcher.start();
        Thread.sleep(100);

        Files.writeString(tempDir.resolve("hello.txt"), "hi");

        await(() -> !created.isEmpty(), 3000);
        assertEquals(1, created.size());
        assertEquals("hello.txt", created.get(0).getFileName().toString());
        watcher.stop();
    }

    @Test
    @DisplayName("detects file modification")
    void detectsFileModification() throws Exception {
        Path file = Files.writeString(tempDir.resolve("edit.txt"), "original");
        List<FileWatcher.Event> events = new CopyOnWriteArrayList<>();
        FileWatcher watcher = new FileWatcher(tempDir, (e, p) -> events.add(e));
        watcher.start();
        Thread.sleep(100);

        Files.writeString(file, "modified");

        await(() -> events.contains(FileWatcher.Event.MODIFIED), 3000);
        assertTrue(events.contains(FileWatcher.Event.MODIFIED));
        watcher.stop();
    }

    @Test
    @DisplayName("detects file deletion")
    void detectsFileDeletion() throws Exception {
        Path file = Files.writeString(tempDir.resolve("bye.txt"), "data");
        List<FileWatcher.Event> events = new CopyOnWriteArrayList<>();
        FileWatcher watcher = new FileWatcher(tempDir, (e, p) -> events.add(e));
        watcher.start();
        Thread.sleep(100);

        Files.delete(file);

        await(() -> events.contains(FileWatcher.Event.DELETED), 3000);
        assertTrue(events.contains(FileWatcher.Event.DELETED));
        watcher.stop();
    }

    @Test
    @DisplayName("constructor rejects a file path (not a directory)")
    void rejectsFilePath() throws Exception {
        Path file = Files.writeString(tempDir.resolve("notadir.txt"), "x");
        assertThrows(IllegalArgumentException.class,
                () -> new FileWatcher(file, (e, p) -> {}));
    }

    @Test
    @DisplayName("start() is idempotent — calling twice does not crash")
    void startIsIdempotent() throws Exception {
        FileWatcher watcher = new FileWatcher(tempDir, (e, p) -> {});
        watcher.start();
        watcher.start(); // should not throw
        assertTrue(watcher.isRunning());
        watcher.stop();
    }

    @Test
    @DisplayName("stop() halts the watcher")
    void stopHaltsWatcher() throws Exception {
        FileWatcher watcher = new FileWatcher(tempDir, (e, p) -> {});
        watcher.start();
        watcher.stop();
        Thread.sleep(100);
        assertFalse(watcher.isRunning());
    }

    /** Helper: polls condition until true or timeout reached. */
    private void await(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline)
                fail("Condition not met within " + timeoutMs + "ms");
            Thread.sleep(50);
        }
    }
}