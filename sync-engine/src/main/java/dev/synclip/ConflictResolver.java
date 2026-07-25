package dev.synclip;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

/**
 * Resolves file sync conflicts when two devices edit the same file offline.
 *
 * Strategy:
 *   1. Last-Write-Wins  the file with the newer timestamp wins
 *   2. Conflict Copy    if timestamps are equal, keep both versions
 *
 * Conflict copy naming:
 *   document.txt → document (conflict 2026-07-13).txt
 */
public class ConflictResolver {

    public enum Resolution { LOCAL_WINS, REMOTE_WINS, CONFLICT_COPY }

    /**
     * Resolves a conflict between a local and remote version of a file.
     *
     * @param localFile path to the local file
     * @param localModified last modified timestamp of local file
     * @param remoteModified last modified timestamp of remote file
     * @return resolution strategy that was applied
     */
    public Resolution resolve(Path localFile,
                              Instant localModified,
                              Instant remoteModified) throws IOException {

        if (localModified.isAfter(remoteModified)) {
            // Local is newer  keep local, discard remote
            return Resolution.LOCAL_WINS;
        }

        if (remoteModified.isAfter(localModified)) {
            // Remote is newer  remote will overwrite local
            return Resolution.REMOTE_WINS;
        }

        // Same timestamp  keep both as conflict copies
        createConflictCopy(localFile);
        return Resolution.CONFLICT_COPY;
    }

    /**
     * Creates a conflict copy of the given file.
     * Example: document.txt → document (conflict 2026-07-13).txt
     */
    public Path createConflictCopy(Path original) throws IOException {
        String filename  = original.getFileName().toString();
        String date      = java.time.LocalDate.now().toString();
        String conflictName;

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            String name = filename.substring(0, dotIndex);
            String ext  = filename.substring(dotIndex);
            conflictName = name + " (conflict " + date + ")" + ext;
        } else {
            conflictName = filename + " (conflict " + date + ")";
        }

        Path conflictPath = original.getParent().resolve(conflictName);
        Files.copy(original, conflictPath, StandardCopyOption.REPLACE_EXISTING);
        return conflictPath;
    }

    /**
     * Checks if a file is a conflict copy.
     */
    public static boolean isConflictCopy(Path file) {
        return file.getFileName().toString().contains("(conflict ");
    }
}