package dev.synclip;

import org.junit.jupiter.api.*;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class SyncManifestTest {

    private SyncManifest manifest;

    @BeforeEach
    void setUp() throws SQLException {
        manifest = new SyncManifest(); // in-memory DB, fresh for every test
    }

    @AfterEach
    void tearDown() throws SQLException {
        manifest.close();
    }

    // -------------------------------------------------------------------------
    // upsert + getStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("new chunk is inserted as PENDING")
    void newChunkIsInsertedAsPending() throws SQLException {
        manifest.upsert("file.txt", 0, "abc123");
        assertEquals(SyncManifest.Status.PENDING, manifest.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("unknown chunk returns null status")
    void unknownChunkReturnsNull() throws SQLException {
        assertNull(manifest.getStatus("ghost.txt", 0));
    }

    @Test
    @DisplayName("markDone sets status to DONE")
    void markDoneSetsStatusToDone() throws SQLException {
        manifest.upsert("file.txt", 0, "abc123");
        manifest.markDone("file.txt", 0);
        assertEquals(SyncManifest.Status.DONE, manifest.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("upsert with same hash keeps status DONE")
    void sameHashKeepsStatusDone() throws SQLException {
        manifest.upsert("file.txt", 0, "abc123");
        manifest.markDone("file.txt", 0);

        manifest.upsert("file.txt", 0, "abc123"); // same hash
        assertEquals(SyncManifest.Status.DONE, manifest.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("upsert with changed hash resets status to PENDING")
    void changedHashResetsStatusToPending() throws SQLException {
        manifest.upsert("file.txt", 0, "abc123");
        manifest.markDone("file.txt", 0);

        manifest.upsert("file.txt", 0, "xyz999"); // hash changed!
        assertEquals(SyncManifest.Status.PENDING, manifest.getStatus("file.txt", 0));
    }

    // -------------------------------------------------------------------------
    // getHash
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getHash returns stored hash")
    void getHashReturnsStoredHash() throws SQLException {
        manifest.upsert("file.txt", 0, "abc123");
        assertEquals("abc123", manifest.getHash("file.txt", 0));
    }

    @Test
    @DisplayName("getHash returns null for unknown chunk")
    void getHashReturnsNullForUnknown() throws SQLException {
        assertNull(manifest.getHash("ghost.txt", 0));
    }

    // -------------------------------------------------------------------------
    // countPending
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("countPending returns correct number of pending chunks")
    void countPendingReturnsCorrectCount() throws SQLException {
        manifest.upsert("file.txt", 0, "h0");
        manifest.upsert("file.txt", 1, "h1");
        manifest.upsert("file.txt", 2, "h2");
        manifest.markDone("file.txt", 0);

        assertEquals(2, manifest.countPending("file.txt"));
    }

    @Test
    @DisplayName("countPending returns 0 when all chunks are done")
    void countPendingZeroWhenAllDone() throws SQLException {
        manifest.upsert("file.txt", 0, "h0");
        manifest.markDone("file.txt", 0);
        assertEquals(0, manifest.countPending("file.txt"));
    }

    // -------------------------------------------------------------------------
    // removeFile
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("removeFile deletes all chunks for a file")
    void removeFileDeletesAllChunks() throws SQLException {
        manifest.upsert("file.txt", 0, "h0");
        manifest.upsert("file.txt", 1, "h1");
        manifest.removeFile("file.txt");

        assertNull(manifest.getStatus("file.txt", 0));
        assertNull(manifest.getStatus("file.txt", 1));
    }

    @Test
    @DisplayName("removeFile does not affect other files")
    void removeFileDoesNotAffectOtherFiles() throws SQLException {
        manifest.upsert("a.txt", 0, "h0");
        manifest.upsert("b.txt", 0, "h1");
        manifest.removeFile("a.txt");

        assertNotNull(manifest.getStatus("b.txt", 0));
    }
}
