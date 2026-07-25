package dev.synclip;

import org.junit.jupiter.api.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class PeerDiscoveryTest {

    @Test
    @DisplayName("PeerInfo stores deviceId, ip, and port correctly")
    void peerInfoStoresValues() {
        var peer = new PeerDiscovery.PeerInfo("mac-abc", "192.168.1.5", 9001);
        assertEquals("mac-abc",     peer.deviceId());
        assertEquals("192.168.1.5", peer.ipAddress());
        assertEquals(9001,          peer.port());
    }

    @Test
    @DisplayName("PeerInfo toString contains all fields")
    void peerInfoToStringContainsAllFields() {
        var peer = new PeerDiscovery.PeerInfo("mac-abc", "192.168.1.5", 9001);
        String str = peer.toString();
        assertTrue(str.contains("mac-abc"));
        assertTrue(str.contains("192.168.1.5"));
        assertTrue(str.contains("9001"));
    }

    @Test
    @DisplayName("start() sets isRunning to true")
    void startSetsIsRunningTrue() throws Exception {
        var peers = new CopyOnWriteArrayList<PeerDiscovery.PeerInfo>();
        var discovery = new PeerDiscovery("mac-test", 9001, peers::add);
        try {
            discovery.start();
            assertTrue(discovery.isRunning());
        } finally {
            discovery.close();
        }
    }

    @Test
    @DisplayName("close() sets isRunning to false")
    void closeSetsIsRunningFalse() throws Exception {
        var discovery = new PeerDiscovery("mac-test", 9001, p -> {});
        discovery.start();
        discovery.close();
        Thread.sleep(100);
        assertFalse(discovery.isRunning());
    }

    @Test
    @DisplayName("start() is idempotent")
    void startIsIdempotent() throws Exception {
        var discovery = new PeerDiscovery("mac-test", 9001, p -> {});
        try {
            discovery.start();
            discovery.start(); // should not throw
            assertTrue(discovery.isRunning());
        } finally {
            discovery.close();
        }
    }

    @Test
    @DisplayName("known peers is empty on start")
    void knownPeersIsEmptyOnStart() throws Exception {
        var discovery = new PeerDiscovery("mac-test", 9001, p -> {});
        try {
            discovery.start();
            assertTrue(discovery.getKnownPeers().isEmpty());
        } finally {
            discovery.close();
        }
    }

    @Test
    @DisplayName("own broadcast is ignored  device does not discover itself")
    void ownBroadcastIsIgnored() throws Exception {
        var discovered = new CopyOnWriteArrayList<PeerDiscovery.PeerInfo>();
        var discovery  = new PeerDiscovery("mac-self", 9001, discovered::add);
        try {
            discovery.start();
            Thread.sleep(3000); // wait for a few broadcast cycles
            assertTrue(discovered.isEmpty(),
                    "Device must not discover itself");
        } finally {
            discovery.close();
        }
    }
}