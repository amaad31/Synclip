package dev.synclip;

import org.junit.jupiter.api.*;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CryptoHelperTest {

    private CryptoHelper crypto;
    private SecretKey key;

    @BeforeEach
    void setUp() throws Exception {
        key    = CryptoHelper.generateKey();
        crypto = new CryptoHelper(key);
    }

    // -------------------------------------------------------------------------
    // Round-trip correctness
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("encrypt then decrypt returns the original plaintext")
    void encryptDecryptRoundTrip() throws Exception {
        byte[] original = "Hello, Synclip!".getBytes();
        byte[] decrypted = crypto.decrypt(crypto.encrypt(original));
        assertArrayEquals(original, decrypted);
    }

    @Test
    @DisplayName("round-trip works for empty plaintext")
    void encryptDecryptEmptyBytes() throws Exception {
        byte[] empty = new byte[0];
        assertArrayEquals(empty, crypto.decrypt(crypto.encrypt(empty)));
    }

    @Test
    @DisplayName("round-trip works for large payload (1 MB)")
    void encryptDecryptLargePayload() throws Exception {
        byte[] large = new byte[1024 * 1024];
        new java.util.Random(42).nextBytes(large);
        assertArrayEquals(large, crypto.decrypt(crypto.encrypt(large)));
    }

    // -------------------------------------------------------------------------
    // Ciphertext properties
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ciphertext length equals plaintext + 12-byte IV + 16-byte tag")
    void ciphertextLengthIsCorrect() throws Exception {
        byte[] plaintext  = new byte[100];
        byte[] ciphertext = crypto.encrypt(plaintext);
        int expected = plaintext.length + CryptoHelper.IV_SIZE_BYTES + (CryptoHelper.TAG_SIZE_BITS / 8);
        assertEquals(expected, ciphertext.length);
    }

    @Test
    @DisplayName("encrypting same plaintext twice produces different ciphertext (random IV)")
    void sameInputProducesDifferentCiphertext() throws Exception {
        byte[] plaintext = "repeat me".getBytes();
        byte[] c1 = crypto.encrypt(plaintext);
        byte[] c2 = crypto.encrypt(plaintext);
        assertFalse(Arrays.equals(c1, c2), "Same plaintext must not produce identical ciphertext");
    }

    @Test
    @DisplayName("ciphertext does not contain the plaintext in readable form")
    void ciphertextDoesNotLeakPlaintext() throws Exception {
        byte[] plaintext  = "secret password".getBytes();
        byte[] ciphertext = crypto.encrypt(plaintext);

        // naive substring check, real crypto review would be more rigorous
        outer:
        for (int i = 0; i <= ciphertext.length - plaintext.length; i++) {
            for (int j = 0; j < plaintext.length; j++) {
                if (ciphertext[i + j] != plaintext[j]) continue outer;
            }
            fail("Plaintext found verbatim inside ciphertext");
        }
    }

    // -------------------------------------------------------------------------
    // Tamper detection (the GCM auth tag catches this)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("flipping one byte in ciphertext causes decryption to fail")
    void tamperedCiphertextRejected() throws Exception {
        byte[] ciphertext = crypto.encrypt("sensitive data".getBytes());
        ciphertext[CryptoHelper.IV_SIZE_BYTES + 3] ^= 0xFF; // flip a byte in ciphertext body
        assertThrows(AEADBadTagException.class, () -> crypto.decrypt(ciphertext));
    }

    @Test
    @DisplayName("flipping one byte in the IV causes decryption to fail")
    void tamperedIVRejected() throws Exception {
        byte[] ciphertext = crypto.encrypt("sensitive data".getBytes());
        ciphertext[2] ^= 0x01; // flip a bit inside the 12-byte IV prefix
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(ciphertext));
    }

    @Test
    @DisplayName("truncated ciphertext is rejected with IllegalArgumentException")
    void truncatedCiphertextRejected() throws Exception {
        byte[] ciphertext = crypto.encrypt("data".getBytes());
        byte[] truncated  = Arrays.copyOf(ciphertext, 10); // way too short
        assertThrows(IllegalArgumentException.class, () -> crypto.decrypt(truncated));
    }

    // -------------------------------------------------------------------------
    // Key handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("wrong key cannot decrypt ciphertext")
    void wrongKeyCannotDecrypt() throws Exception {
        byte[] ciphertext = crypto.encrypt("top secret".getBytes());
        CryptoHelper wrongCrypto = new CryptoHelper(CryptoHelper.generateKey());
        assertThrows(GeneralSecurityException.class, () -> wrongCrypto.decrypt(ciphertext));
    }

    @Test
    @DisplayName("key serialised to bytes and restored produces same key")
    void keyRoundTripFromBytes() throws Exception {
        byte[] rawKey     = key.getEncoded();
        SecretKey restored = CryptoHelper.keyFromBytes(rawKey);
        assertArrayEquals(rawKey, restored.getEncoded());
    }

    @Test
    @DisplayName("keyFromBytes rejects arrays that are not 32 bytes")
    void keyFromBytesRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> CryptoHelper.keyFromBytes(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> CryptoHelper.keyFromBytes(new byte[64]));
    }

    @Test
    @DisplayName("non-AES key is rejected by constructor")
    void nonAesKeyRejected() throws Exception {
        KeyGeneratorHelper dsaKey = new KeyGeneratorHelper();
        assertThrows(IllegalArgumentException.class, dsaKey::buildAndPassToConstructor);
    }

    /** Small helper to build a non-AES key for the rejection test above. */
    static class KeyGeneratorHelper {
        void buildAndPassToConstructor() throws Exception {
            javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance("HmacSHA256");
            SecretKey hmacKey = kg.generateKey();
            new CryptoHelper(hmacKey); // must throw
        }
    }
}
