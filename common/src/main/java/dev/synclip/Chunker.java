package dev.synclip;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Splits a file into fixed-size chunks and computes a SHA-256 hash for each.
 * Only chunks whose hash has changed need to be re-uploaded (delta sync).
 */
public class Chunker {

    public static final int DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024; // 4 MB

    private final int chunkSize;

    public Chunker() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public Chunker(int chunkSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("Chunk size must be > 0");
        this.chunkSize = chunkSize;
    }

    /**
     * Splits the given file into chunks. Each chunk carries its index,
     * raw bytes, and a SHA-256 hex hash.
     */
    public List<Chunk> split(Path file) throws IOException {
        List<Chunk> chunks = new ArrayList<>();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[chunkSize];
            int index = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                String hash = sha256Hex(data);
                chunks.add(new Chunk(index++, data, hash));
            }
        }

        return chunks;
    }

    /** Computes SHA-256 of raw bytes and returns lowercase hex string. */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public int getChunkSize() {
        return chunkSize;
    }

    /** Immutable value object representing one chunk of a file. */
    public record Chunk(int index, byte[] data, String hash) {

        public int size() {
            return data.length;
        }

        @Override
        public String toString() {
            return "Chunk[index=" + index + ", size=" + size() + ", hash=" + hash.substring(0, 8) + "...]";
        }
    }
}
