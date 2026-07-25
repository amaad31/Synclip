package dev.synclip;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConflictResolverTest {

    @TempDir Path tempDir;

    private ConflictResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ConflictResolver();
    }

    // -------------------------------------------------------------------------
    // Last-Write-Wins
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("local wins when local timestamp is newer")
    void localWinsWhenLocalIsNewer() throws Exception {
        Path file = Files.writeString(tempDir.resolve("doc.txt"), "local");
        Instant local  = Instant.now();
        Instant remote = local.minusSeconds(60);

        var result = resolver.resolve(file, local, remote);
        assertEquals(ConflictResolver.Resolution.LOCAL_WINS, result);
    }

    @Test
    @DisplayName("remote wins when remote timestamp is newer")
    void remoteWinsWhenRemoteIsNewer() throws Exception {
        Path file = Files.writeString(tempDir.resolve("doc.txt"), "local");
        Instant local  = Instant.now().minusSeconds(60);
        Instant remote = Instant.now();

        var result = resolver.resolve(file, local, remote);
        assertEquals(ConflictResolver.Resolution.REMOTE_WINS, result);
    }

    @Test
    @DisplayName("conflict copy created when timestamps are equal")
    void conflictCopyCreatedWhenTimestampsEqual() throws Exception {
        Path file = Files.writeString(tempDir.resolve("doc.txt"), "content");
        Instant same = Instant.now();

        var result = resolver.resolve(file, same, same);
        assertEquals(ConflictResolver.Resolution.CONFLICT_COPY, result);
    }

    // -------------------------------------------------------------------------
    // Conflict copy naming
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("conflict copy has correct name format with extension")
    void conflictCopyNameFormatWithExtension() throws Exception {
        Path file = Files.writeString(tempDir.resolve("document.txt"), "data");
        Path copy = resolver.createConflictCopy(file);

        String name = copy.getFileName().toString();
        assertTrue(name.startsWith("document"));
        assertTrue(name.contains("(conflict "));
        assertTrue(name.endsWith(".txt"));
    }

    @Test
    @DisplayName("conflict copy has correct name format without extension")
    void conflictCopyNameFormatWithoutExtension() throws Exception {
        Path file = Files.writeString(tempDir.resolve("Makefile"), "data");
        Path copy = resolver.createConflictCopy(file);

        String name = copy.getFileName().toString();
        assertTrue(name.startsWith("Makefile"));
        assertTrue(name.contains("(conflict "));
    }

    @Test
    @DisplayName("conflict copy contains same content as original")
    void conflictCopyContainsSameContent() throws Exception {
        Path file = Files.writeString(tempDir.resolve("note.txt"), "important note");
        Path copy = resolver.createConflictCopy(file);

        assertEquals("important note", Files.readString(copy));
    }

    @Test
    @DisplayName("original file is not modified after conflict copy")
    void originalFileUnchangedAfterConflictCopy() throws Exception {
        Path file = Files.writeString(tempDir.resolve("note.txt"), "original");
        resolver.createConflictCopy(file);
        assertEquals("original", Files.readString(file));
    }

    // -------------------------------------------------------------------------
    // isConflictCopy
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isConflictCopy returns true for conflict files")
    void isConflictCopyReturnsTrueForConflictFiles() {
        Path conflict = Path.of("document (conflict 2026-07-13).txt");
        assertTrue(ConflictResolver.isConflictCopy(conflict));
    }

    @Test
    @DisplayName("isConflictCopy returns false for normal files")
    void isConflictCopyReturnsFalseForNormalFiles() {
        Path normal = Path.of("document.txt");
        assertFalse(ConflictResolver.isConflictCopy(normal));
    }
}