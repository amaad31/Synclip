package dev.synclip;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.BiConsumer;

/**
 * Watches a directory for file changes using the OS-level WatchService API.
 * On macOS this uses FSEvents, on Linux inotify — no polling.
 *
 * Usage:
 *   FileWatcher watcher = new FileWatcher(path, (event, file) -> {
 *       System.out.println(event + " → " + file);
 *   });
 *   watcher.start();
 */
public class FileWatcher {

    public enum Event { CREATED, MODIFIED, DELETED }

    private final Path watchDir;
    private final BiConsumer<Event, Path> listener;
    private volatile boolean running = false;
    private Thread watchThread;

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

        WatchService watchService = FileSystems.getDefault().newWatchService();
        watchDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        watchThread = new Thread(() -> runLoop(watchService), "filewatcher-thread");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /** Stops the watcher. */
    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
    }

    public boolean isRunning() { return running; }

    private void runLoop(WatchService watchService) {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take(); // blocks until an event arrives
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path changed = watchDir.resolve(((WatchEvent<Path>) event).context());

                if (kind == StandardWatchEventKinds.ENTRY_CREATE)
                    listener.accept(Event.CREATED, changed);
                else if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
                    listener.accept(Event.MODIFIED, changed);
                else if (kind == StandardWatchEventKinds.ENTRY_DELETE)
                    listener.accept(Event.DELETED, changed);
            }

            if (!key.reset()) break; // directory no longer accessible
        }
    }
}
