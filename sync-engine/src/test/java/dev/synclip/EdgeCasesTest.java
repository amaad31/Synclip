package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for the sync pipeline.
 * Covers: empty files, large files, binary files,
 * rapid edits, special characters in filenames.
 */
class EdgeCasesTest {

    @TempDir Path tempDir;

    private SyncManifest manifest;
    private DeltaSync deltaSync;

    @BeforeEach
    void setUp() throws Exception {
        manifest  = new SyncManifest("mac-edge");
        deltaSync = new DeltaSync(new Chunker(100), manifest);
    }

    @AfterEach
    void tearDown() throws Exception {
        manifest.close();
    }

    // -------------------------------------------------------------------------
    // Empty file
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("empty file produces no pending chunks")
    void emptyFileProducesNoPendingChunks() throws Exception {
        Path file = Files.writeString(tempDir.resolve("empty.txt"), "");
        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        assertEquals(0, pending.size());
    }

    @Test
    @DisplayName("empty file is immediately considered fully synced")
    void emptyFileIsFullySynced() throws Exception {
        Path file = Files.writeString(tempDir.resolve("empty.txt"), "");
        assertTrue(deltaSync.isFullySynced(file));
    }

    // -------------------------------------------------------------------------
    // Binary files
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("binary file is chunked and hashed correctly")
    void binaryFileChunkedCorrectly() throws Exception {
        byte[] binary = new byte[300];
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) i;

        Path file = tempDir.resolve("binary.bin");
        Files.write(file, binary);

        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        assertEquals(3, pending.size()); // 300 bytes / 100 = 3 chunks
    }

    @Test
    @DisplayName("binary file round-trips through encryption correctly")
    void binaryFileEncryptionRoundTrip() throws Exception {
        byte[] binary = new byte[256];
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) i;

        var key    = CryptoHelper.generateKey();
        var crypto = new CryptoHelper(key);

        byte[] encrypted = crypto.encrypt(binary);
        byte[] decrypted = crypto.decrypt(encrypted);

        assertArrayEquals(binary, decrypted);
    }

    // -------------------------------------------------------------------------
    // Large files
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("large file (1MB) is split into correct number of chunks")
    void largeFileChunkedCorrectly() throws Exception {
        byte[] large = new byte[1024 * 1024]; // 1 MB
        Path file = tempDir.resolve("large.bin");
        Files.write(file, large);

        Chunker chunker = new Chunker(4 * 1024); // 4 KB chunks
        List<Chunker.Chunk> chunks = chunker.split(file);

        assertEquals(256, chunks.size()); // 1MB / 4KB = 256 chunks
    }

    @Test
    @DisplayName("large file reassembles correctly after chunking")
    void largeFileReassemblesCorrectly() throws Exception {
        byte[] original = new byte[500];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i % 256);

        Path file = tempDir.resolve("medium.bin");
        Files.write(file, original);

        List<Chunker.Chunk> chunks = new Chunker(100).split(file);

        // Reassemble
        byte[] reassembled = new byte[original.length];
        int offset = 0;
        for (Chunker.Chunk chunk : chunks) {
            System.arraycopy(chunk.data(), 0, reassembled, offset, chunk.size());
            offset += chunk.size();
        }

        assertArrayEquals(original, reassembled);
    }

    // -------------------------------------------------------------------------
    // Rapid edits
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rapid edits  only final state is pending after each sync")
    void rapidEditsOnlyFinalStateIsPending() throws Exception {
        Path file = Files.writeString(tempDir.resolve("rapid.txt"), "version 1");

        // Sync version 1
        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        String filePath = file.toAbsolutePath().toString();
        for (Chunker.Chunk c : pending) manifest.markDone(filePath, c.index());

        // Rapid edits
        Files.writeString(file, "version 2");
        Files.writeString(file, "version 3");
        Files.writeString(file, "version 4  final");

        // Only final state matters
        pending = deltaSync.pendingChunks(file);
        assertEquals(1, pending.size());
    }

    // -------------------------------------------------------------------------
    // Special characters in filenames
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("filename with spaces is handled correctly")
    void filenameWithSpacesHandledCorrectly() throws Exception {
        Path file = Files.writeString(
                tempDir.resolve("my document file.txt"), "content");
        assertDoesNotThrow(() -> deltaSync.pendingChunks(file));
    }

    @Test
    @DisplayName("filename with unicode characters is handled correctly")
    void filenameWithUnicodeHandledCorrectly() throws Exception {
        Path file = Files.writeString(
                tempDir.resolve("Ünïcödé_fïlé.txt"), "content");
        assertDoesNotThrow(() -> deltaSync.pendingChunks(file));
    }

    // -------------------------------------------------------------------------
    // RetryPolicy edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("retry succeeds on third attempt")
    void retrySucceedsOnThirdAttempt() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger(0);
        var retry = new RetryPolicy(3, 0);

        retry.execute(() -> {
            if (count.incrementAndGet() < 3)
                throw new java.io.IOException("not yet");
        });

        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("tampered chunk is rejected by CryptoHelper")
    void tamperedChunkIsRejected() throws Exception {
        var key    = CryptoHelper.generateKey();
        var crypto = new CryptoHelper(key);

        byte[] encrypted = crypto.encrypt("secret".getBytes());
        encrypted[CryptoHelper.IV_SIZE_BYTES + 1] ^= 0xFF; // tamper

        assertThrows(Exception.class, () -> crypto.decrypt(encrypted));
    }
}