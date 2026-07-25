package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Phase4EdgeCasesTest {

    @TempDir Path tempDir;
    private SyncManifest manifest;

    @BeforeEach
    void setUp() throws Exception { manifest = new SyncManifest("mac-edge4"); }

    @AfterEach
    void tearDown() throws Exception { manifest.close(); }

    @Test
    @DisplayName("conflict copy of file with multiple dots in name")
    void conflictCopyWithMultipleDotsInName() throws Exception {
        Path file = Files.writeString(tempDir.resolve("archive.tar.gz"), "data");
        Path copy = new ConflictResolver().createConflictCopy(file);
        assertTrue(copy.getFileName().toString().endsWith(".gz"));
        assertTrue(copy.getFileName().toString().contains("(conflict "));
    }

    @Test
    @DisplayName("queue handles 1000 events without data loss")
    void queueHandles1000Events() {
        OfflineQueue queue = new OfflineQueue(false);
        for (int i = 0; i < 1000; i++)
            queue.enqueue(FileWatcher.Event.MODIFIED, Path.of("file" + i + ".txt"));
        assertEquals(1000, queue.size());
        assertEquals(1000, queue.flush().size());
    }

    @Test
    @DisplayName("flush twice returns empty list on second call")
    void flushTwiceReturnsEmptyOnSecond() {
        OfflineQueue queue = new OfflineQueue(false);
        queue.enqueue(FileWatcher.Event.CREATED, Path.of("file.txt"));
        queue.flush();
        assertTrue(queue.flush().isEmpty());
    }

    @Test
    @DisplayName("single byte change in large file is detected")
    void singleByteChangeInLargeFileIsDetected() throws Exception {
        byte[] data = new byte[500];
        Path file = tempDir.resolve("flip.bin");
        Files.write(file, data);
        String filePath = file.toAbsolutePath().toString();
        Chunker chunker = new Chunker(100);
        List<Chunker.Chunk> chunks = chunker.split(file);
        for (Chunker.Chunk c : chunks) {
            manifest.upsert(filePath, c.index(), c.hash());
            manifest.markDone(filePath, c.index());
        }
        data[201] = (byte) 0xFF;
        Files.write(file, data);
        IntegrityChecker checker = new IntegrityChecker(chunker, manifest);
        List<Integer> corrupted = checker.verify(file);
        assertEquals(1, corrupted.size());
        assertEquals(2, corrupted.get(0));
    }
}