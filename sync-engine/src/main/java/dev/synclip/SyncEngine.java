package dev.synclip;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * The core orchestrator of Synclip's file sync pipeline.
 *
 * Wires together:
 *   FileWatcher -> Chunker -> SyncManifest -> UploadWorker
 *
 * Flow:
 *   1. FileWatcher detects a change
 *   2. SyncEngine receives the event
 *   3. Chunker splits the file into chunks
 *   4. SyncManifest upserts each chunk (sets changed ones to PENDING)
 *   5. UploadWorker uploads all PENDING chunks in parallel
 *   6. On DELETE -> SyncManifest removes all chunk records for that file
 */
public class SyncEngine implements AutoCloseable {

    private final Chunker chunker;
    private final SyncManifest manifest;
    private final UploadWorker uploadWorker;
    private final List<FileWatcher> watchers;

    public SyncEngine(Chunker chunker,
                      SyncManifest manifest,
                      UploadWorker uploadWorker,
                      List<Path> watchDirs) throws IOException {
        this.chunker      = chunker;
        this.manifest     = manifest;
        this.uploadWorker = uploadWorker;
        this.watchers     = new java.util.ArrayList<>();

        for (Path dir : watchDirs) {
            FileWatcher watcher = new FileWatcher(dir, this::handleEvent);
            watchers.add(watcher);
        }
    }

    /** Starts all FileWatchers — begins listening for file changes. */
    public void start() throws IOException {
        for (FileWatcher watcher : watchers) {
            watcher.start();
        }
    }

    /** Stops all FileWatchers and shuts down the upload thread pool. */
    @Override
    public void close() {
        for (FileWatcher watcher : watchers) {
            watcher.stop();
        }
        uploadWorker.close();
    }

    /**
     * Handles a file system event from FileWatcher.
     * Called from the FileWatcher background thread.
     */
    void handleEvent(FileWatcher.Event event, Path path) {
        try {
            switch (event) {
                case CREATED, MODIFIED -> syncFile(path);
                case DELETED           -> removeFile(path);
            }
        } catch (Exception e) {
            System.err.println("[SyncEngine] Error handling event "
                    + event + " for " + path + ": " + e.getMessage());
        }
    }

    /**
     * Syncs a file — chunk it, update manifest, upload PENDING chunks.
     */
    private void syncFile(Path path) throws Exception {
        if (!Files.exists(path) || !Files.isRegularFile(path)) return;

        String filePath = path.toAbsolutePath().toString();
        List<Chunker.Chunk> chunks = chunker.split(path);

        // Upsert each chunk — changed ones become PENDING
        for (Chunker.Chunk chunk : chunks) {
            manifest.upsert(filePath, chunk.index(), chunk.hash());
        }

        // Upload all PENDING chunks in parallel
        uploadWorker.uploadAll(filePath, chunks);
    }

    /**
     * Removes all chunk records for a deleted file.
     */
    private void removeFile(Path path) throws Exception {
        String filePath = path.toAbsolutePath().toString();
        manifest.removeFile(filePath);
    }

    public List<FileWatcher> getWatchers() { return watchers; }
}