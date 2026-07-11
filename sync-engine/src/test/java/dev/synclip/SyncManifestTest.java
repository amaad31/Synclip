package dev.synclip;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncManifestTest {

    private SyncManifest mac1;
    private SyncManifest mac2;

    @BeforeEach
    void setUp() throws Exception {
        mac1 = new SyncManifest("mac-amaad");
        mac2 = new SyncManifest("windows-amaad");
    }

    @AfterEach
    void tearDown() throws Exception {
        mac1.close();
        mac2.close();
    }

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("new chunk is inserted as PENDING")
    void newChunkIsInsertedAsPending() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        assertEquals(SyncManifest.Status.PENDING, mac1.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("markDone setzt Status auf DONE")
    void markDoneSetsStatusToDone() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac1.markDone("file.txt", 0);
        assertEquals(SyncManifest.Status.DONE, mac1.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("same hash keeps status DONE, delta sync")
    void sameHashKeepsStatusDone() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac1.markDone("file.txt", 0);
        mac1.upsert("file.txt", 0, "abc123"); // same hash
        assertEquals(SyncManifest.Status.DONE, mac1.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("changed hash resets status to PENDING")
    void changedHashResetsStatusToPending() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac1.markDone("file.txt", 0);
        mac1.upsert("file.txt", 0, "xyz999"); // hash changed!
        assertEquals(SyncManifest.Status.PENDING, mac1.getStatus("file.txt", 0));
    }

    // -------------------------------------------------------------------------
    // Device-ID isolation, new in Day 5
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("two devices track the same chunk independently")
    void twoDevicesTrackIndependently() throws Exception {
        mac1.upsert("foto.jpg", 0, "abc123");
        mac2.upsert("foto.jpg", 0, "abc123");

        mac1.markDone("foto.jpg", 0);

        // Mac 1 is done, Mac 2 is still pending
        assertEquals(SyncManifest.Status.DONE,    mac1.getStatus("foto.jpg", 0));
        assertEquals(SyncManifest.Status.PENDING, mac2.getStatus("foto.jpg", 0));
    }

    @Test
    @DisplayName("device 1 cannot see chunks from device 2")
    void deviceCannotSeeOtherDeviceChunks() throws Exception {
        mac2.upsert("secret.txt", 0, "abc123");

        // mac1 never inserted this, must return null
        assertNull(mac1.getStatus("secret.txt", 0));
    }

    @Test
    @DisplayName("removeFile deletes only the current device's chunks")
    void removeFileOnlyAffectsOwnDevice() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac2.upsert("file.txt", 0, "abc123");

        mac1.removeFile("file.txt");

        // mac1 deleted, mac2 untouched
        assertNull(mac1.getStatus("file.txt", 0));
        assertNotNull(mac2.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("countPending counts only PENDING chunks for the current device")
    void countPendingOnlyCountsOwnDevice() throws Exception {
        mac1.upsert("file.txt", 0, "h0");
        mac1.upsert("file.txt", 1, "h1");
        mac1.markDone("file.txt", 0);

        mac2.upsert("file.txt", 0, "h0");
        mac2.upsert("file.txt", 1, "h1");
        mac2.upsert("file.txt", 2, "h2");

        // mac1 has 1 pending, mac2 has 3 pending separately!
        assertEquals(1, mac1.countPending("file.txt"));
        assertEquals(3, mac2.countPending("file.txt"));
    }

    @Test
    @DisplayName("getDeviceId returns the correct ID")
    void getDeviceIdReturnsCorrectId() throws Exception {
        assertEquals("mac-amaad", mac1.getDeviceId());
        assertEquals("windows-amaad",  mac2.getDeviceId());
    }
}