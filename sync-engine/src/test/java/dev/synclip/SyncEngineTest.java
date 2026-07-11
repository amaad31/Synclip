package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SyncEngineTest {

    @TempDir Path watchDir;

    private SyncManifest manifest;
    private FakeSyncEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        manifest = new SyncManifest("mac-test");
        engine   = new FakeSyncEngine(manifest, new Chunker(1024));
    }

    @AfterEach
    void tearDown() throws Exception {
        manifest.close();
    }

    @Test
    @DisplayName("CREATED event -> chunks are upserted into the manifest")
    void createdEventUpsertChunksInManifest() throws Exception {
        Path file = Files.writeString(watchDir.resolve("note.txt"), "hello synclip");
        engine.handleEvent(FileWatcher.Event.CREATED, file);

        // Chunk 0 must be present in the manifest
        assertNotNull(manifest.getStatus(file.toAbsolutePath().toString(), 0));
    }

    @Test
    @DisplayName("MODIFIED event -> changed hash resets chunk to PENDING")
    void modifiedEventResetsPendingOnHashChange() throws Exception {
        Path file = Files.writeString(watchDir.resolve("doc.txt"), "version 1");
        engine.handleEvent(FileWatcher.Event.CREATED, file);

        // Mark as DONE
        manifest.markDone(file.toAbsolutePath().toString(), 0);
        assertEquals(SyncManifest.Status.DONE,
                manifest.getStatus(file.toAbsolutePath().toString(), 0));

        // Modify file
        Files.writeString(file, "version 2 — completely different");
        engine.handleEvent(FileWatcher.Event.MODIFIED, file);

        // Hash changed -> back to PENDING
        assertEquals(SyncManifest.Status.PENDING,
                manifest.getStatus(file.toAbsolutePath().toString(), 0));
    }

    @Test
    @DisplayName("DELETED event -> chunks are removed from the manifest")
    void deletedEventRemovesChunksFromManifest() throws Exception {
        Path file = Files.writeString(watchDir.resolve("bye.txt"), "data");
        engine.handleEvent(FileWatcher.Event.CREATED, file);
        assertNotNull(manifest.getStatus(file.toAbsolutePath().toString(), 0));

        engine.handleEvent(FileWatcher.Event.DELETED, file);
        assertNull(manifest.getStatus(file.toAbsolutePath().toString(), 0));
    }

    @Test
    @DisplayName("empty file -> does not crash")
    void emptyFileDoesNotCrash() throws Exception {
        Path file = Files.writeString(watchDir.resolve("empty.txt"), "");
        assertDoesNotThrow(() ->
                engine.handleEvent(FileWatcher.Event.CREATED, file));
    }

    @Test
    @DisplayName("non-existent file -> does not crash")
    void nonExistentFileDoesNotCrash() throws Exception {
        Path ghost = watchDir.resolve("ghost.txt");
        assertDoesNotThrow(() ->
                engine.handleEvent(FileWatcher.Event.CREATED, ghost));
    }

    // -------------------------------------------------------------------------
    // Fake SyncEngine — no real UploadWorker required
    // -------------------------------------------------------------------------

    static class FakeSyncEngine {
        private final SyncManifest manifest;
        private final Chunker chunker;
        final List<String> uploadedFiles = new CopyOnWriteArrayList<>();

        FakeSyncEngine(SyncManifest manifest, Chunker chunker) {
            this.manifest = manifest;
            this.chunker  = chunker;
        }

        void handleEvent(FileWatcher.Event event, Path path) throws Exception {
            switch (event) {
                case CREATED, MODIFIED -> syncFile(path);
                case DELETED           -> manifest.removeFile(
                        path.toAbsolutePath().toString());
            }
        }

        private void syncFile(Path path) throws Exception {
            if (!Files.exists(path) || !Files.isRegularFile(path)) return;
            String filePath = path.toAbsolutePath().toString();
            List<Chunker.Chunk> chunks = chunker.split(path);
            for (Chunker.Chunk chunk : chunks) {
                manifest.upsert(filePath, chunk.index(), chunk.hash());
            }
            uploadedFiles.add(filePath);
        }
    }
}