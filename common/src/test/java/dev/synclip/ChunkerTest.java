package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Basic splitting behaviour
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("empty file produces zero chunks")
    void emptyFileProducesNoChunks() throws IOException {
        Path file = tempDir.resolve("empty.bin");
        Files.write(file, new byte[0]);

        List<Chunker.Chunk> chunks = new Chunker(1024).split(file);

        assertTrue(chunks.isEmpty(), "Expected no chunks for an empty file");
    }

    @Test
    @DisplayName("file smaller than chunk size fits in exactly one chunk")
    void smallFileFitsInOneChunk() throws IOException {
        byte[] content = "hello synclip".getBytes();
        Path file = tempDir.resolve("small.txt");
        Files.write(file, content);

        List<Chunker.Chunk> chunks = new Chunker(1024).split(file);

        assertEquals(1, chunks.size());
        assertArrayEquals(content, chunks.get(0).data());
        assertEquals(0, chunks.get(0).index());
    }

    @Test
    @DisplayName("file exactly chunk-size produces one full chunk")
    void fileExactlyChunkSize() throws IOException {
        int size = 512;
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 0xAB);
        Path file = tempDir.resolve("exact.bin");
        Files.write(file, content);

        List<Chunker.Chunk> chunks = new Chunker(size).split(file);

        assertEquals(1, chunks.size());
        assertEquals(size, chunks.get(0).size());
    }

    @Test
    @DisplayName("file larger than chunk size splits into multiple chunks")
    void largeFileSplitsIntoMultipleChunks() throws IOException {
        int chunkSize = 100;
        byte[] content = new byte[350]; // expect 4 chunks: 100+100+100+50
        Arrays.fill(content, (byte) 1);
        Path file = tempDir.resolve("large.bin");
        Files.write(file, content);

        List<Chunker.Chunk> chunks = new Chunker(chunkSize).split(file);

        assertEquals(4, chunks.size());
        assertEquals(100, chunks.get(0).size());
        assertEquals(100, chunks.get(1).size());
        assertEquals(100, chunks.get(2).size());
        assertEquals(50,  chunks.get(3).size());
    }

    @Test
    @DisplayName("chunk indices are sequential starting at 0")
    void chunkIndicesAreSequential() throws IOException {
        byte[] content = new byte[300];
        Path file = tempDir.resolve("seq.bin");
        Files.write(file, content);

        List<Chunker.Chunk> chunks = new Chunker(100).split(file);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
        }
    }

    @Test
    @DisplayName("reassembling all chunk data reconstructs the original file")
    void chunksReconstructOriginalFile() throws IOException {
        byte[] original = new byte[500];
        for (int i = 0; i < original.length; i++) original[i] = (byte) i;
        Path file = tempDir.resolve("reconstruct.bin");
        Files.write(file, original);

        List<Chunker.Chunk> chunks = new Chunker(128).split(file);

        // stitch together
        byte[] reconstructed = new byte[original.length];
        int offset = 0;
        for (Chunker.Chunk c : chunks) {
            System.arraycopy(c.data(), 0, reconstructed, offset, c.size());
            offset += c.size();
        }

        assertArrayEquals(original, reconstructed);
    }

    // -------------------------------------------------------------------------
    // Hashing correctness
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("each chunk carries a 64-char SHA-256 hex hash")
    void eachChunkHasCorrectHashLength() throws IOException {
        Path file = tempDir.resolve("hash.bin");
        Files.write(file, new byte[256]);

        List<Chunker.Chunk> chunks = new Chunker(100).split(file);

        for (Chunker.Chunk c : chunks) {
            assertNotNull(c.hash());
            assertEquals(64, c.hash().length(), "SHA-256 hex must be 64 chars");
            assertTrue(c.hash().matches("[0-9a-f]{64}"), "Hash must be lowercase hex");
        }
    }

    @Test
    @DisplayName("identical chunk data produces identical hash (deterministic)")
    void identicalDataProducesIdenticalHash() throws IOException {
        byte[] content = "deterministic".getBytes();
        Path f1 = tempDir.resolve("a.txt");
        Path f2 = tempDir.resolve("b.txt");
        Files.write(f1, content);
        Files.write(f2, content);

        String hash1 = new Chunker(1024).split(f1).get(0).hash();
        String hash2 = new Chunker(1024).split(f2).get(0).hash();

        assertEquals(hash1, hash2, "Same content must produce same hash");
    }

    @Test
    @DisplayName("different chunk data produces different hash")
    void differentDataProducesDifferentHash() {
        String h1 = Chunker.sha256Hex("hello".getBytes());
        String h2 = Chunker.sha256Hex("world".getBytes());
        assertNotEquals(h1, h2);
    }

    @Test
    @DisplayName("modifying one byte changes the chunk hash (delta detection)")
    void singleByteChangeAltersHash() throws IOException {
        byte[] original = new byte[64];
        Path file = tempDir.resolve("delta.bin");
        Files.write(file, original);
        String hashBefore = new Chunker(1024).split(file).get(0).hash();

        original[0] = (byte) 0xFF; // flip one byte
        Files.write(file, original);
        String hashAfter = new Chunker(1024).split(file).get(0).hash();

        assertNotEquals(hashBefore, hashAfter, "A 1-byte change must change the hash");
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("chunk size of 1 byte splits file into N individual-byte chunks")
    void chunkSizeOfOneByteWorks() throws IOException {
        byte[] content = {10, 20, 30};
        Path file = tempDir.resolve("single.bin");
        Files.write(file, content);

        List<Chunker.Chunk> chunks = new Chunker(1).split(file);

        assertEquals(3, chunks.size());
        assertEquals(1, chunks.get(0).size());
    }

    @Test
    @DisplayName("constructor rejects non-positive chunk size")
    void invalidChunkSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Chunker(0));
        assertThrows(IllegalArgumentException.class, () -> new Chunker(-1));
    }

    @Test
    @DisplayName("splitting a non-existent file throws IOException")
    void missingFileThrowsIOException() {
        Path missing = tempDir.resolve("ghost.bin");
        assertThrows(IOException.class, () -> new Chunker().split(missing));
    }
}
