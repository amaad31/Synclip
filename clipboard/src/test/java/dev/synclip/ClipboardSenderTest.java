package dev.synclip;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.GraphicsEnvironment;

class ClipboardSenderTest {

    private CryptoHelper crypto;
    private ClipboardManager clipboardManager;
    private ClipboardSender sender;

    @BeforeAll
    static void checkDisplay() {
        Assumptions.assumeFalse(
            GraphicsEnvironment.isHeadless(),
            "No display available -> skipping clipboard tests"
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        crypto           = new CryptoHelper(CryptoHelper.generateKey());
        clipboardManager = new ClipboardManager();
        sender           = new ClipboardSender(crypto, clipboardManager);
        clipboardManager.clear();
    }

    @Test
    @DisplayName("sends encrypted payload to a listening server")
    void sendsEncryptedPayloadToServer() throws Exception {
        clipboardManager.writeText("hello from mac");

        // Start a fake receiver on a random port
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();

        Future<byte[]> received = Executors.newSingleThreadExecutor().submit(() -> {
            try (Socket conn = server.accept();
                 DataInputStream in = new DataInputStream(conn.getInputStream())) {
                int length = in.readInt();
                byte[] data = new byte[length];
                in.readFully(data);
                server.close();
                return data;
            }
        });

        sender.sendToPeer("127.0.0.1", port);

        byte[] encrypted = received.get(3, TimeUnit.SECONDS);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);

        // Verify it decrypts back to original
        byte[] decrypted = crypto.decrypt(encrypted);
        assertEquals("hello from mac", new String(decrypted, "UTF-8"));
    }

    @Test
    @DisplayName("empty clipboard sends nothing")
    void emptyClipboardSendsNothing() throws Exception {
        clipboardManager.clear();

        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();

        AtomicBoolean connected = new java.util.concurrent.atomic.AtomicBoolean(false);
        server.setSoTimeout(500);

        sender.sendToPeer("127.0.0.1", port);

        assertFalse(connected.get(), "Nothing should be sent for empty clipboard");
        server.close();
    }

    @Test
    @DisplayName("payload is encrypted  not readable as plaintext")
    void payloadIsEncrypted() throws Exception {
        String secret = "top secret clipboard content";
        clipboardManager.writeText(secret);

        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();

        Future<byte[]> received = Executors.newSingleThreadExecutor().submit(() -> {
            try (Socket conn = server.accept();
                 DataInputStream in = new DataInputStream(conn.getInputStream())) {
                int length = in.readInt();
                byte[] data = new byte[length];
                in.readFully(data);
                server.close();
                return data;
            }
        });

        sender.sendToPeer("127.0.0.1", port);
        byte[] encrypted = received.get(3, TimeUnit.SECONDS);

        // Encrypted payload must not contain plaintext
        assertFalse(new String(encrypted).contains(secret),
                "Encrypted payload must not contain plaintext");
    }

    @Test
    @DisplayName("sendBytes encrypts and sends raw bytes")
    void sendBytesEncryptsAndSends() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};

        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();

        Future<byte[]> received = Executors.newSingleThreadExecutor().submit(() -> {
            try (Socket conn = server.accept();
                 DataInputStream in = new DataInputStream(conn.getInputStream())) {
                int length = in.readInt();
                byte[] payload = new byte[length];
                in.readFully(payload);
                server.close();
                return payload;
            }
        });

        sender.sendBytes(data, "127.0.0.1", port);
        byte[] encrypted = received.get(3, TimeUnit.SECONDS);
        byte[] decrypted = crypto.decrypt(encrypted);

        assertArrayEquals(data, decrypted);
    }
}