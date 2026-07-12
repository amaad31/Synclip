package dev.synclip;

import org.junit.jupiter.api.*;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardReceiverTest {

    private CryptoHelper crypto;
    private ClipboardReceiver receiver;
    private int port;

    @BeforeAll
    static void checkDisplay() {
        Assumptions.assumeFalse(
            GraphicsEnvironment.isHeadless(),
            "No display available -> skipping clipboard tests"
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        crypto   = new CryptoHelper(CryptoHelper.generateKey());
        port     = findFreePort();
        receiver = new ClipboardReceiver(port, crypto, new ClipboardManager());
    }

    @AfterEach
    void tearDown() {
        receiver.close();
    }

    @Test
    @DisplayName("start() sets isRunning to true")
    void startSetsIsRunningTrue() throws Exception {
        receiver.start();
        assertTrue(receiver.isRunning());
    }

    @Test
    @DisplayName("close() sets isRunning to false")
    void closeSetsIsRunningFalse() throws Exception {
        receiver.start();
        receiver.close();
        Thread.sleep(100);
        assertFalse(receiver.isRunning());
    }

    @Test
    @DisplayName("start() is idempotent")
    void startIsIdempotent() throws Exception {
        receiver.start();
        receiver.start();
        assertTrue(receiver.isRunning());
    }

    @Test
    @DisplayName("receives and decrypts clipboard content from sender")
    void receivesAndDecryptsContent() throws Exception {
        ClipboardManager clipboardManager = new ClipboardManager();
        receiver = new ClipboardReceiver(port, crypto, clipboardManager);
        receiver.start();
        Thread.sleep(100);

        // Send encrypted payload directly
        byte[] encrypted = crypto.encrypt("hello from sender".getBytes("UTF-8"));
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            out.writeInt(encrypted.length);
            out.write(encrypted);
            out.flush();
        }

        Thread.sleep(200);
        assertEquals("hello from sender", clipboardManager.readText());
    }

    @Test
    @DisplayName("sender and receiver full round-trip")
    void fullSenderReceiverRoundTrip() throws Exception {
        ClipboardManager senderClipboard   = new ClipboardManager();
        ClipboardManager receiverClipboard = new ClipboardManager();

        senderClipboard.writeText("cross-device clipboard!");

        receiver = new ClipboardReceiver(port, crypto, receiverClipboard);
        receiver.start();
        Thread.sleep(100);

        ClipboardSender sender = new ClipboardSender(crypto, senderClipboard);
        sender.sendToPeer("127.0.0.1", port);

        Thread.sleep(200);
        assertEquals("cross-device clipboard!", receiverClipboard.readText());
    }

    // -------------------------------------------------------------------------

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}