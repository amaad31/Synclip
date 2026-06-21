package dev.synclip;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiConsumer;

/**
 * Watches a directory recursively for file changes using the OS-level WatchService API.
 * On macOS this uses FSEvents, on Linux inotify — no polling.
 *
 * Automatically registers new subdirectories as they are created.
 */
public class FileWatcher {

    public enum Event { CREATED, MODIFIED, DELETED }

    private final Path watchDir;
    private final BiConsumer<Event, Path> listener;
    private volatile boolean running = false;
    private Thread watchThread;
    private WatchService watchService;

    public FileWatcher(Path watchDir, BiConsumer<Event, Path> listener) {
        if (!Files.isDirectory(watchDir))
            throw new IllegalArgumentException("Path must be a directory: " + watchDir);
        this.watchDir = watchDir;
        this.listener = listener;
    }

    /** Starts watching in a background thread. Returns immediately. */
    public void start() throws IOException {
        if (running) return;
        running = true;

        watchService = FileSystems.getDefault().newWatchService();
        registerAll(watchDir); // register root + all existing subdirectories

        watchThread = new Thread(() -> runLoop(), "filewatcher-thread");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /** Stops the watcher. */
    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running; }

    /**
     * Registers the given directory and all subdirectories recursively.
     * Called once on start(), and again whenever a new subdirectory is created.
     */
    private void registerAll(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void runLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take(); // blocks until an event arrives
            } catch (InterruptedException | ClosedWatchServiceException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // The WatchKey knows which directory it belongs to
            Path dir = (Path) key.watchable();

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path changed = dir.resolve(((WatchEvent<Path>) event).context());

                // If a new directory was created, register it too
                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(changed)) {
                    try {
                        registerAll(changed);
                        Thread.sleep(50);
                    } catch (IOException | InterruptedException ignored) {}
                }

                // Only fire listener for files, not directories
                if (!Files.isDirectory(changed)) {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE)
                        listener.accept(Event.CREATED, changed);
                    else if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
                        listener.accept(Event.MODIFIED, changed);
                    else if (kind == StandardWatchEventKinds.ENTRY_DELETE)
                        listener.accept(Event.DELETED, changed);
                }
            }

            if (!key.reset()) break;
        }
    }
}
