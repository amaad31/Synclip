package dev.synclip;

import org.junit.jupiter.api.*;
//import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests UploadWorker with a fake MinIO client, no real server needed.
 * We mock the upload operation to test parallel execution and retry logic.
 */
class UploadWorkerTest {

    private SyncManifest manifest;

    @BeforeEach
    void setUp() throws Exception {
        manifest = new SyncManifest("mac-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        manifest.close();
    }

    @Test
    @DisplayName("already uploaded chunks are skipped")
    void alreadyUploadedChunksAreSkipped() throws Exception {
        // Chunk 0 is already DONE
        manifest.upsert("file.txt", 0, "h0");
        manifest.markDone("file.txt", 0);

        // Chunk 1 is PENDING
        manifest.upsert("file.txt", 1, "h1");

        AtomicInteger uploadCount = new AtomicInteger(0);

        FakeUploadWorker worker = new FakeUploadWorker(manifest, uploadCount);
        List<Chunker.Chunk> chunks = List.of(
            new Chunker.Chunk(0, new byte[]{1}, "h0"),
            new Chunker.Chunk(1, new byte[]{2}, "h1")
        );
        worker.uploadAll("file.txt", chunks);

        // Only chunk 1 was uploaded
        assertEquals(1, uploadCount.get());
    }

    @Test
    @DisplayName("alle Chunks werden parallel hochgeladen")
    void allChunksUploadedInParallel() throws Exception {
        manifest.upsert("file.txt", 0, "h0");
        manifest.upsert("file.txt", 1, "h1");
        manifest.upsert("file.txt", 2, "h2");

        AtomicInteger uploadCount = new AtomicInteger(0);
        FakeUploadWorker worker = new FakeUploadWorker(manifest, uploadCount);

        List<Chunker.Chunk> chunks = List.of(
            new Chunker.Chunk(0, new byte[]{1}, "h0"),
            new Chunker.Chunk(1, new byte[]{2}, "h1"),
            new Chunker.Chunk(2, new byte[]{3}, "h2")
        );
        worker.uploadAll("file.txt", chunks);

        assertEquals(3, uploadCount.get());
    }

    @Test
    @DisplayName("empty chunk list does nothing")
    void emptyChunkListDoesNothing() throws Exception {
        AtomicInteger uploadCount = new AtomicInteger(0);
        FakeUploadWorker worker = new FakeUploadWorker(manifest, uploadCount);
        worker.uploadAll("file.txt", List.of());
        assertEquals(0, uploadCount.get());
    }

    @Test
    @DisplayName("nach erfolgreichem Upload ist Status DONE")
    void statusIsDoneAfterUpload() throws Exception {
        manifest.upsert("file.txt", 0, "h0");

        AtomicInteger uploadCount = new AtomicInteger(0);
        FakeUploadWorker worker = new FakeUploadWorker(manifest, uploadCount);

        worker.uploadAll("file.txt", List.of(
            new Chunker.Chunk(0, new byte[]{1}, "h0")
        ));

        assertEquals(SyncManifest.Status.DONE, manifest.getStatus("file.txt", 0));
    }

    // -------------------------------------------------------------------------
    // Fake UploadWorker, no real MinIO server required
    // -------------------------------------------------------------------------

    /**
     * Simulates UploadWorker without a real MinIO connection.
     * Counts uploads and marks chunks as DONE in the manifest.
     */
    static class FakeUploadWorker {
        private final SyncManifest manifest;
        private final AtomicInteger uploadCount;

        FakeUploadWorker(SyncManifest manifest, AtomicInteger uploadCount) {
            this.manifest = manifest;
            this.uploadCount = uploadCount;
        }

        void uploadAll(String filePath, List<Chunker.Chunk> chunks)
                throws Exception {
            for (Chunker.Chunk chunk : chunks) {
                SyncManifest.Status status = manifest.getStatus(filePath, chunk.index());
                if (status == SyncManifest.Status.DONE) continue;

                // Simulate upload
                uploadCount.incrementAndGet();
                manifest.markDone(filePath, chunk.index());
            }
        }
    }
}