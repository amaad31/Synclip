package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeltaSyncTest {

    @TempDir Path tempDir;

    private SyncManifest manifest;
    private DeltaSync deltaSync;

    @BeforeEach
    void setUp() throws Exception {
        manifest  = new SyncManifest("mac-test");
        deltaSync = new DeltaSync(new Chunker(100), manifest);
    }

    @AfterEach
    void tearDown() throws Exception {
        manifest.close();
    }

    @Test
    @DisplayName("new file returns all chunks as pending")
    void newFileReturnsAllChunksPending() throws Exception {
        Path file = Files.writeString(tempDir.resolve("new.txt"),
                "a".repeat(250)); // 3 chunks at 100 bytes each

        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);

        assertEquals(3, pending.size());
    }

    @Test
    @DisplayName("fully uploaded file returns zero pending chunks")
    void fullyUploadedFileReturnsNoPending() throws Exception {
        Path file = Files.writeString(tempDir.resolve("done.txt"), "hello");
        String filePath = file.toAbsolutePath().toString();

        // Mark all chunks as DONE
        List<Chunker.Chunk> chunks = new Chunker(100).split(file);
        for (Chunker.Chunk chunk : chunks) {
            manifest.upsert(filePath, chunk.index(), chunk.hash());
            manifest.markDone(filePath, chunk.index());
        }

        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        assertEquals(0, pending.size());
    }

    @Test
    @DisplayName("only changed chunk is returned as pending")
    void onlyChangedChunkReturnedAsPending() throws Exception {
        Path file = Files.writeString(tempDir.resolve("partial.txt"),
                "a".repeat(200)); // 2 chunks
        String filePath = file.toAbsolutePath().toString();

        // Mark both chunks as DONE
        List<Chunker.Chunk> chunks = new Chunker(100).split(file);
        for (Chunker.Chunk chunk : chunks) {
            manifest.upsert(filePath, chunk.index(), chunk.hash());
            manifest.markDone(filePath, chunk.index());
        }

        // Modify file — second chunk changes
        Files.writeString(file, "a".repeat(100) + "b".repeat(100));

        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        assertEquals(1, pending.size());
        assertEquals(1, pending.get(0).index()); // chunk 1 changed
    }

    @Test
    @DisplayName("isFullySynced returns false when chunks are pending")
    void isFullySyncedReturnsFalseWhenPending() throws Exception {
        Path file = Files.writeString(tempDir.resolve("pending.txt"), "data");
        assertFalse(deltaSync.isFullySynced(file));
    }

    @Test
    @DisplayName("isFullySynced returns true when all chunks are done")
    void isFullySyncedReturnsTrueWhenAllDone() throws Exception {
        Path file = Files.writeString(tempDir.resolve("synced.txt"), "data");
        String filePath = file.toAbsolutePath().toString();

        List<Chunker.Chunk> chunks = new Chunker(100).split(file);
        for (Chunker.Chunk chunk : chunks) {
            manifest.upsert(filePath, chunk.index(), chunk.hash());
            manifest.markDone(filePath, chunk.index());
        }

        assertTrue(deltaSync.isFullySynced(file));
    }

    @Test
    @DisplayName("empty file returns zero pending chunks")
    void emptyFileReturnsNoPending() throws Exception {
        Path file = Files.writeString(tempDir.resolve("empty.txt"), "");
        List<Chunker.Chunk> pending = deltaSync.pendingChunks(file);
        assertEquals(0, pending.size());
    }
}