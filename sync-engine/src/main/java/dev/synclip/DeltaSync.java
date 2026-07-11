package dev.synclip;

import java.nio.file.*;
import java.util.*;

/**
 * Computes which chunks need to be uploaded by comparing
 * current file chunks against the SyncManifest.
 *
 * Only chunks whose hash has changed or are new will be returned.
 * Chunks already marked DONE with the same hash are skipped entirely.
 */
public class DeltaSync {

    private final Chunker chunker;
    private final SyncManifest manifest;

    public DeltaSync(Chunker chunker, SyncManifest manifest) {
        this.chunker  = chunker;
        this.manifest = manifest;
    }

    /**
     * Returns only the chunks that need uploading for the given file.
     * DONE + same hash → skipped
     * PENDING or changed hash → included
     */
    public List<Chunker.Chunk> pendingChunks(Path file) throws Exception {
        String filePath = file.toAbsolutePath().toString();
        List<Chunker.Chunk> all = chunker.split(file);
        List<Chunker.Chunk> pending = new ArrayList<>();

        for (Chunker.Chunk chunk : all) {
            manifest.upsert(filePath, chunk.index(), chunk.hash());

            SyncManifest.Status status = manifest.getStatus(filePath, chunk.index());
            if (status != SyncManifest.Status.DONE) {
                pending.add(chunk);
            }
        }

        return pending;
    }

    /**
     * Returns true if all chunks for the given file are DONE.
     * Useful to check if a file is fully synced.
     */
    public boolean isFullySynced(Path file) throws Exception {
        String filePath = file.toAbsolutePath().toString();
        List<Chunker.Chunk> all = chunker.split(file);

        for (Chunker.Chunk chunk : all) {
            SyncManifest.Status status = manifest.getStatus(filePath, chunk.index());
            if (status != SyncManifest.Status.DONE) return false;
        }

        return true;
    }
}