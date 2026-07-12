package dev.synclip;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;

/**
 * Reads and writes the system clipboard using Java's AWT Toolkit.
 * Works on macOS, Windows, and Linux without any native dependencies.
 *
 * Supported content types:
 *   - Plain text (String)
 *   - Raw bytes (byte[]) for binary content
 */
public class ClipboardManager {

    private final Clipboard clipboard;

    public ClipboardManager() {
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    /**
     * Reads the current clipboard content as a String.
     * Returns null if clipboard is empty or contains non-text content.
     */
    public String readText() {
        try {
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null;
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException e) {
            return null;
        }
    }

    /**
     * Writes a String to the system clipboard.
     */
    public void writeText(String text) {
        if (text == null) throw new IllegalArgumentException("Text must not be null");
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, selection);
    }

    /**
     * Reads clipboard content as raw bytes.
     * Returns null if clipboard is empty or content cannot be read.
     */
    public byte[] readBytes() {
        String text = readText();
        if (text == null) return null;
        return text.getBytes();
    }

    /**
     * Writes raw bytes to the clipboard as a String.
     * Used for binary content like encrypted clipboard payloads.
     */
    public void writeBytes(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Data must not be null");
        writeText(new String(data));
    }

    /**
     * Returns true if the clipboard currently contains text.
     */
    public boolean hasText() {
        return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
    }

    /**
     * Clears the clipboard content.
     */
    public void clear() {
        clipboard.setContents(new StringSelection(""), null);
    }
}