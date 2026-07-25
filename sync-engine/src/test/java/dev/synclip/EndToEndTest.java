package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test simulating the full sync pipeline:
 * FileWatcher -> Chunker -> SyncManifest -> DeltaSync -> UploadWorker (fake)
 */
class EndToEndTest {

    @TempDir Path watchDir;

    private SyncManifest manifest;
    private Chunker chunker;
    private DeltaSync deltaSync;
    private List<String> uploadedChunks;

    @BeforeEach
    void setUp() throws Exception {
        manifest      = new SyncManifest("mac-e2e");
        chunker       = new Chunker(100);
        deltaSync     = new DeltaSync(chunker, manifest);
        uploadedChunks = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        manifest.close();
    }

    @Test
    @DisplayName("full pipeline: create file -> chunk -> upload all chunks")
    void fullPipelineNewFile() throws Exception {
        Path file = Files.writeString(watchDir.resolve("report.txt"),
                "a".repeat(250)); // 3 chunks

        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        fakeUpload(file.toAbsolutePath().toString(), pending);

        assertEquals(3, uploadedChunks.size());
        assertTrue(deltaSync.isFullySynced(file));
    }

    @Test
    @DisplayName("second sync of unchanged file uploads nothing")
    void secondSyncOfUnchangedFileUploadsNothing() throws Exception {
        Path file = Files.writeString(watchDir.resolve("stable.txt"), "hello");

        // First sync
        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        fakeUpload(file.toAbsolutePath().toString(), pending);
        uploadedChunks.clear();

        // Second sync  nothing changed
        pending = deltaSync.pendingChunks(file);
        fakeUpload(file.toAbsolutePath().toString(), pending);

        assertEquals(0, uploadedChunks.size());
    }

    @Test
    @DisplayName("modifying one chunk only re-uploads that chunk")
    void modifyingOneChunkOnlyReuploadsIt() throws Exception {
        Path file = Files.writeString(watchDir.resolve("doc.txt"),
                "a".repeat(200)); // 2 chunks

        // First sync  upload all
        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        fakeUpload(file.toAbsolutePath().toString(), pending);
        uploadedChunks.clear();

        // Modify only second chunk
        Files.writeString(file, "a".repeat(100) + "b".repeat(100));

        // Second sync  only chunk 1 should be re-uploaded
        pending = deltaSync.pendingChunks(file);
        fakeUpload(file.toAbsolutePath().toString(), pending);

        assertEquals(1, uploadedChunks.size());
    }

    @Test
    @DisplayName("encrypted chunks are different from plaintext")
    void encryptedChunksAreDifferentFromPlaintext() throws Exception {
        byte[] plaintext = "sensitive data".getBytes();
        var key = CryptoHelper.generateKey();
        var crypto = new CryptoHelper(key);

        byte[] encrypted = crypto.encrypt(plaintext);
        byte[] decrypted = crypto.decrypt(encrypted);

        assertNotEquals(new String(plaintext), new String(encrypted));
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("delete event removes file from manifest")
    void deleteEventRemovesFileFromManifest() throws Exception {
        Path file = Files.writeString(watchDir.resolve("bye.txt"), "data");
        String filePath = file.toAbsolutePath().toString();

        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        fakeUpload(filePath, pending);
        assertNotNull(manifest.getStatus(filePath, 0));

        manifest.removeFile(filePath);
        assertNull(manifest.getStatus(filePath, 0));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void fakeUpload(String filePath, List<Chunker.Chunk> chunks)
            throws Exception {
        for (Chunker.Chunk chunk : chunks) {
            uploadedChunks.add(filePath + "/chunk_" + chunk.index());
            manifest.markDone(filePath, chunk.index());
        }
    }
}