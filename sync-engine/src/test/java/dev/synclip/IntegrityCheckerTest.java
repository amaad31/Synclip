package dev.synclip;

import java.nio.file.*;
import java.util.*;

public class IntegrityCheckerTest {

    private final Chunker chunker;
    private final SyncManifest manifest;

    public IntegrityCheckerTest(Chunker chunker, SyncManifest manifest) {
        this.chunker  = chunker;
        this.manifest = manifest;
    }

    public List<Integer> verify(Path file) throws Exception {
        String filePath = file.toAbsolutePath().toString();
        List<Chunker.Chunk> chunks = chunker.split(file);
        List<Integer> corrupted = new ArrayList<>();
        for (Chunker.Chunk chunk : chunks) {
            String storedHash = manifest.getHash(filePath, chunk.index());
            if (storedHash == null) { corrupted.add(chunk.index()); continue; }
            if (!storedHash.equals(chunk.hash())) {
                corrupted.add(chunk.index());
                manifest.upsert(filePath, chunk.index(), chunk.hash());
            }
        }
        return corrupted;
    }

    public boolean isIntact(Path file) throws Exception {
        return verify(file).isEmpty();
    }

    public static boolean verifyChunk(Chunker.Chunk chunk, String expectedHash) {
        return chunk.hash().equals(expectedHash);
    }
}