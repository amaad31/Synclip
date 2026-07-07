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
    // Grundfunktionen
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("neuer Chunk wird als PENDING eingefügt")
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
    @DisplayName("gleicher Hash behält Status DONE — Delta Sync")
    void sameHashKeepsStatusDone() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac1.markDone("file.txt", 0);
        mac1.upsert("file.txt", 0, "abc123"); // gleicher Hash
        assertEquals(SyncManifest.Status.DONE, mac1.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("geänderter Hash setzt Status zurück auf PENDING")
    void changedHashResetsStatusToPending() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac1.markDone("file.txt", 0);
        mac1.upsert("file.txt", 0, "xyz999"); // Hash geändert!
        assertEquals(SyncManifest.Status.PENDING, mac1.getStatus("file.txt", 0));
    }

    // -------------------------------------------------------------------------
    // Device-ID Isolation — das Neue in Day 5
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("zwei Geräte tracken denselben Chunk unabhängig voneinander")
    void twoDevicesTrackIndependently() throws Exception {
        mac1.upsert("foto.jpg", 0, "abc123");
        mac2.upsert("foto.jpg", 0, "abc123");

        mac1.markDone("foto.jpg", 0);

        // Mac 1 ist fertig, Mac 2 noch nicht
        assertEquals(SyncManifest.Status.DONE,    mac1.getStatus("foto.jpg", 0));
        assertEquals(SyncManifest.Status.PENDING, mac2.getStatus("foto.jpg", 0));
    }

    @Test
    @DisplayName("Gerät 1 sieht keine Chunks von Gerät 2")
    void deviceCannotSeeOtherDeviceChunks() throws Exception {
        mac2.upsert("secret.txt", 0, "abc123");

        // mac1 hat das nie eingefügt — muss null zurückgeben
        assertNull(mac1.getStatus("secret.txt", 0));
    }

    @Test
    @DisplayName("removeFile löscht nur Chunks des eigenen Geräts")
    void removeFileOnlyAffectsOwnDevice() throws Exception {
        mac1.upsert("file.txt", 0, "abc123");
        mac2.upsert("file.txt", 0, "abc123");

        mac1.removeFile("file.txt");

        // mac1 gelöscht, mac2 unberührt
        assertNull(mac1.getStatus("file.txt", 0));
        assertNotNull(mac2.getStatus("file.txt", 0));
    }

    @Test
    @DisplayName("countPending zählt nur PENDING Chunks des eigenen Geräts")
    void countPendingOnlyCountsOwnDevice() throws Exception {
        mac1.upsert("file.txt", 0, "h0");
        mac1.upsert("file.txt", 1, "h1");
        mac1.markDone("file.txt", 0);

        mac2.upsert("file.txt", 0, "h0");
        mac2.upsert("file.txt", 1, "h1");
        mac2.upsert("file.txt", 2, "h2");

        // mac1 hat 1 pending, mac2 hat 3 pending — getrennt!
        assertEquals(1, mac1.countPending("file.txt"));
        assertEquals(3, mac2.countPending("file.txt"));
    }

    @Test
    @DisplayName("getDeviceId gibt die korrekte ID zurück")
    void getDeviceIdReturnsCorrectId() throws Exception {
        assertEquals("mac-amaad", mac1.getDeviceId());
        assertEquals("windows-amaad",  mac2.getDeviceId());
    }
}