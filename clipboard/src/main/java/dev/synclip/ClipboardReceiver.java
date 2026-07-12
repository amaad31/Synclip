package dev.synclip;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Listens for incoming encrypted clipboard payloads over TCP.
 * Handles multiple concurrent connections using a thread pool.
 *
 * Flow:
 *   1. Listen on a TCP port
 *   2. Accept incoming connection from a sender
 *   3. Read length-prefixed encrypted payload
 *   4. Decrypt with AES-256-GCM
 *   5. Write decrypted content to system clipboard
 */
public class ClipboardReceiver implements AutoCloseable {

    private final int port;
    private final CryptoHelper crypto;
    private final ClipboardManager clipboardManager;
    private final ExecutorService threadPool;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public ClipboardReceiver(int port,
                             CryptoHelper crypto,
                             ClipboardManager clipboardManager) {
        this.port             = port;
        this.crypto           = crypto;
        this.clipboardManager = clipboardManager;
        this.threadPool       = Executors.newCachedThreadPool();
    }

    /**
     * Starts the receiver — listens for incoming connections in the background.
     * Returns immediately.
     */
    public void start() throws IOException {
        if (running) return;
        running      = true;
        serverSocket = new ServerSocket(port);

        acceptThread = new Thread(this::acceptLoop, "clipboard-receiver");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Accepts incoming connections and hands each off to the thread pool.
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                threadPool.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) System.err.println("[ClipboardReceiver] Accept error: " + e.getMessage());
            }
        }
    }

    /**
     * Reads, decrypts, and writes clipboard content from one connection.
     */
    private void handleConnection(Socket client) {
        try (client;
             DataInputStream in = new DataInputStream(client.getInputStream())) {

            int length    = in.readInt();
            byte[] encrypted = new byte[length];
            in.readFully(encrypted);

            byte[] decrypted = crypto.decrypt(encrypted);
            clipboardManager.writeText(new String(decrypted, "UTF-8"));

        } catch (Exception e) {
            System.err.println("[ClipboardReceiver] Error handling connection: " + e.getMessage());
        }
    }

    public boolean isRunning() { return running; }
    public int getPort()       { return port; }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        threadPool.shutdown();
        if (acceptThread != null) acceptThread.interrupt();
    }
}