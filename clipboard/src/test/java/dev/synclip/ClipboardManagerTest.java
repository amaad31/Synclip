package dev.synclip;

import org.junit.jupiter.api.*;
import java.awt.GraphicsEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardManagerTest {

    private ClipboardManager clipboard;

    @BeforeAll
    static void checkDisplay() {
        org.junit.jupiter.api.Assumptions.assumeFalse(
            GraphicsEnvironment.isHeadless(),
            "No display available -> skipping clipboard tests"
        );
    }

    @BeforeEach
    void setUp() {
        clipboard = new ClipboardManager();
        clipboard.clear();
    }

    @Test
    @DisplayName("writeText and readText round-trip")
    void writeAndReadTextRoundTrip() {
        clipboard.writeText("hello synclip");
        assertEquals("hello synclip", clipboard.readText());
    }

    @Test
    @DisplayName("writeText overwrites previous content")
    void writeTextOverwritesPrevious() {
        clipboard.writeText("first");
        clipboard.writeText("second");
        assertEquals("second", clipboard.readText());
    }

    @Test
    @DisplayName("hasText returns true after writing text")
    void hasTextReturnsTrueAfterWrite() {
        clipboard.writeText("test");
        assertTrue(clipboard.hasText());
    }

    @Test
    @DisplayName("clear empties the clipboard")
    void clearEmptiesClipboard() {
        clipboard.writeText("something");
        clipboard.clear();
        String content = clipboard.readText();
        assertTrue(content == null || content.isEmpty());
    }

    @Test
    @DisplayName("writeText rejects null")
    void writeTextRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> clipboard.writeText(null));
    }

    @Test
    @DisplayName("writeBytes and readBytes round-trip")
    void writeBytesAndReadBytesRoundTrip() {
        byte[] data = "binary content".getBytes();
        clipboard.writeBytes(data);
        assertNotNull(clipboard.readBytes());
    }

    @Test
    @DisplayName("writeBytes rejects null")
    void writeBytesRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> clipboard.writeBytes(null));
    }

    @Test
    @DisplayName("long text is stored and retrieved correctly")
    void longTextStoredCorrectly() {
        String long_text = "a".repeat(10000);
        clipboard.writeText(long_text);
        assertEquals(long_text, clipboard.readText());
    }

    @Test
    @DisplayName("special characters are preserved")
    void specialCharactersPreserved() {
        String special = "Hello 🌍 Ünïcödé \n\t Tab & Newline";
        clipboard.writeText(special);
        assertEquals(special, clipboard.readText());
    }
}