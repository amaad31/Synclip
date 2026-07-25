package dev.synclip;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;

/**
 * AES-256-GCM authenticated encryption helper.
 *
 * // #FOR_ME
 * Why AES-GCM 
 *  - AES-256: military-grade symmetric encryption
 *  - GCM mode: also authenticates the ciphertext, detects tampering
 *  - Each encryption uses a random 12-byte IV so the same plaintext
 *    always produces different ciphertext (prevents pattern analysis)
 *
 * Wire format of encrypt() output:
 *   [ 12 bytes IV ] [ N bytes ciphertext + 16 bytes GCM auth tag ]
 */
public class CryptoHelper {

    public static final int KEY_SIZE_BITS = 256;
    public static final int IV_SIZE_BYTES = 12;   // 96-bit IV recommended for GCM
    public static final int TAG_SIZE_BITS = 128;  // 16-byte authentication tag
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom rng = new SecureRandom();

    /** Create a helper with the given AES-256 key. */
    public CryptoHelper(SecretKey key) {
        if (!"AES".equals(key.getAlgorithm()))
            throw new IllegalArgumentException("Key must be AES");
        this.key = key;
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plaintext raw bytes to encrypt
     * @return IV (12 bytes) + ciphertext + GCM auth tag (16 bytes)
     */
    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_SIZE_BYTES];
        rng.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // prepend IV so the receiver can extract it
        byte[] output = new byte[IV_SIZE_BYTES + ciphertext.length];
        System.arraycopy(iv,         0, output, 0,             IV_SIZE_BYTES);
        System.arraycopy(ciphertext, 0, output, IV_SIZE_BYTES, ciphertext.length);
        return output;
    }

    /**
     * Decrypts an encrypt() output back to plaintext.
     * Throws AEADBadTagException if the ciphertext was tampered with.
     *
     * @param ivAndCiphertext IV + ciphertext + GCM tag (output of encrypt())
     * @return original plaintext bytes
     */
    public byte[] decrypt(byte[] ivAndCiphertext) throws GeneralSecurityException {
        if (ivAndCiphertext.length < IV_SIZE_BYTES + 16)
            throw new IllegalArgumentException("Input too short to be valid ciphertext");

        byte[] iv         = new byte[IV_SIZE_BYTES];
        byte[] ciphertext = new byte[ivAndCiphertext.length - IV_SIZE_BYTES];
        System.arraycopy(ivAndCiphertext, 0,             iv,         0, IV_SIZE_BYTES);
        System.arraycopy(ivAndCiphertext, IV_SIZE_BYTES, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    // -------------------------------------------------------------------------
    // Key generation utilities
    // -------------------------------------------------------------------------

    /** Generates a fresh random AES-256 key. Use this during device pairing. */
    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(KEY_SIZE_BITS, new SecureRandom());
        return kg.generateKey();
    }

    /**
     * Reconstructs a SecretKey from raw bytes (e.g. loaded from disk or
     * received via a key-exchange protocol like ECDH).
     */
    public static SecretKey keyFromBytes(byte[] rawKey) {
        if (rawKey.length != 32)
            throw new IllegalArgumentException("AES-256 requires exactly 32 key bytes");
        return new SecretKeySpec(rawKey, "AES");
    }
}
