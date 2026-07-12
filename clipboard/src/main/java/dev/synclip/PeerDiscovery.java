package dev.synclip;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Discovers other Synclip devices on the local network using UDP broadcast.
 *
 * How it works:
 *   1. Every device broadcasts its presence every 2 seconds
 *   2. Every device listens for broadcasts from other devices
 *   3. When a new peer is discovered, the listener is notified
 *
 * Broadcast message format:
 *   SYNCLIP:<deviceId>:<port>
 *   e.g. "SYNCLIP:mac-a3f9b2c1:9001"
 */
public class PeerDiscovery implements AutoCloseable {

    private static final int    BROADCAST_PORT    = 9090;
    private static final int    BROADCAST_INTERVAL_MS = 2000;
    private static final String MESSAGE_PREFIX    = "SYNCLIP:";
    private static final int    SOCKET_TIMEOUT_MS = 3000;

    private final String deviceId;
    private final int    servicePort;
    private final Consumer<PeerInfo> onPeerDiscovered;
    private final Map<String, PeerInfo> knownPeers = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private Thread senderThread;
    private Thread receiverThread;
    private DatagramSocket senderSocket;
    private DatagramSocket receiverSocket;

    public PeerDiscovery(String deviceId,
                         int servicePort,
                         Consumer<PeerInfo> onPeerDiscovered) {
        this.deviceId         = deviceId;
        this.servicePort      = servicePort;
        this.onPeerDiscovered = onPeerDiscovered;
    }

    /** Starts broadcasting and listening for peers. */
    public void start() throws IOException {
        if (running) return;
        running = true;

        senderSocket   = new DatagramSocket();
        senderSocket.setBroadcast(true);

        receiverSocket = new DatagramSocket(BROADCAST_PORT);
        receiverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

        senderThread   = new Thread(this::broadcastLoop, "peer-sender");
        receiverThread = new Thread(this::receiveLoop,   "peer-receiver");

        senderThread.setDaemon(true);
        receiverThread.setDaemon(true);

        senderThread.start();
        receiverThread.start();
    }

    /** Broadcasts our presence to the network every 2 seconds. */
    private void broadcastLoop() {
        String message = MESSAGE_PREFIX + deviceId + ":" + servicePort;
        byte[] data    = message.getBytes();

        while (running) {
            try {
                InetAddress broadcast = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(
                        data, data.length, broadcast, BROADCAST_PORT);
                senderSocket.send(packet);
                Thread.sleep(BROADCAST_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException ignored) {}
        }
    }

    /** Listens for broadcasts from other devices. */
    private void receiveLoop() {
        byte[] buffer = new byte[256];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiverSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                handleMessage(message, packet.getAddress());

            } catch (SocketTimeoutException ignored) {
                // normal — keep looping
            } catch (IOException e) {
                if (running) System.err.println("[PeerDiscovery] Receive error: " + e.getMessage());
            }
        }
    }

    /** Parses a broadcast message and notifies listener if it's a new peer. */
    private void handleMessage(String message, InetAddress address) {
        if (!message.startsWith(MESSAGE_PREFIX)) return;

        String[] parts = message.substring(MESSAGE_PREFIX.length()).split(":");
        if (parts.length != 2) return;

        String peerId   = parts[0];
        int    peerPort;
        try { peerPort = Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) { return; }

        // Ignore our own broadcasts
        if (peerId.equals(deviceId)) return;

        // Notify only on first discovery
        if (!knownPeers.containsKey(peerId)) {
            PeerInfo peer = new PeerInfo(peerId, address.getHostAddress(), peerPort);
            knownPeers.put(peerId, peer);
            onPeerDiscovered.accept(peer);
        }
    }

    /** Returns all currently known peers. */
    public Collection<PeerInfo> getKnownPeers() {
        return Collections.unmodifiableCollection(knownPeers.values());
    }

    public boolean isRunning() { return running; }

    @Override
    public void close() {
        running = false;
        if (senderThread   != null) senderThread.interrupt();
        if (receiverThread != null) receiverThread.interrupt();
        if (senderSocket   != null) senderSocket.close();
        if (receiverSocket != null) receiverSocket.close();
    }

    // -------------------------------------------------------------------------

    /** Immutable value object representing a discovered peer. */
    public record PeerInfo(String deviceId, String ipAddress, int port) {
        @Override
        public String toString() {
            return deviceId + "@" + ipAddress + ":" + port;
        }
    }
}