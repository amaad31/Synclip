package dev.synclip;

import java.io.*;
import java.net.*;

/**
 * Sends encrypted clipboard content to a peer over TCP.
 *
 * Wire format:
 *   [ 4 bytes: payload length ] [ N bytes: AES-256-GCM encrypted payload ]
 *
 * Flow:
 *   1. Read current clipboard content
 *   2. Encrypt with AES-256-GCM
 *   3. Send to peer via TCP socket
 */
public class ClipboardSender {

    private final CryptoHelper crypto;
    private final ClipboardManager clipboardManager;

    public ClipboardSender(CryptoHelper crypto, ClipboardManager clipboardManager) {
        this.crypto           = crypto;
        this.clipboardManager = clipboardManager;
    }

    /**
     * Reads the current clipboard and sends it encrypted to the given peer.
     *
     * @param peerIp   IP address of the target device
     * @param peerPort TCP port the receiver is listening on
     */
    public void sendToPeer(String peerIp, int peerPort) throws Exception {
        String text = clipboardManager.readText();
        if (text == null || text.isEmpty()) return;

        byte[] plaintext  = text.getBytes("UTF-8");
        byte[] encrypted  = crypto.encrypt(plaintext);

        try (Socket socket = new Socket(peerIp, peerPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Send length prefix first so receiver knows how many bytes to read
            out.writeInt(encrypted.length);
            out.write(encrypted);
            out.flush();
        }
    }

    /**
     * Sends arbitrary bytes encrypted to a peer.
     * Useful for binary clipboard content.
     */
    public void sendBytes(byte[] data, String peerIp, int peerPort) throws Exception {
        if (data == null || data.length == 0) return;

        byte[] encrypted = crypto.encrypt(data);

        try (Socket socket = new Socket(peerIp, peerPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            out.writeInt(encrypted.length);
            out.write(encrypted);
            out.flush();
        }
    }
}