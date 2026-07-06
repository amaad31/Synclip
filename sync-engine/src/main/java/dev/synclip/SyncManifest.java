package dev.synclip;

import java.nio.file.Path;
import java.sql.*;

/**
 * Tracks which chunks have been uploaded using a local SQLite database.
 * This enables delta sync — only upload chunks that are new or changed.
 *
 * Schema:
 *   chunks(file_path TEXT, chunk_index INT, hash TEXT, status TEXT)
 *   status = PENDING | DONE
 */
public class SyncManifest implements AutoCloseable {

    public enum Status { PENDING, DONE }

    private final Connection conn;

    public SyncManifest(Path dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        initSchema();
    }

    /** In-memory database — useful for tests. */
    public SyncManifest() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    file_path   TEXT    NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    hash        TEXT    NOT NULL,
                    status      TEXT    NOT NULL DEFAULT 'PENDING',
                    PRIMARY KEY (file_path, chunk_index)
                )
            """);
        }
    }

    /**
     * Inserts or updates a chunk record.
     * If the chunk already exists with the same hash → status stays DONE.
     * If the hash changed → status resets to PENDING (needs re-upload).
     */
    public void upsert(String filePath, int chunkIndex, String hash) throws SQLException {
        String existing = getHash(filePath, chunkIndex);

        if (existing == null) {
            // New chunk — insert as PENDING
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO chunks(file_path, chunk_index, hash, status) VALUES(?,?,?,'PENDING')")) {
                ps.setString(1, filePath);
                ps.setInt(2, chunkIndex);
                ps.setString(3, hash);
                ps.executeUpdate();
            }
        } else if (!existing.equals(hash)) {
            // Hash changed — mark as PENDING so it gets re-uploaded
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE chunks SET hash=?, status='PENDING' WHERE file_path=? AND chunk_index=?")) {
                ps.setString(1, hash);
                ps.setString(2, filePath);
                ps.setInt(3, chunkIndex);
                ps.executeUpdate();
            }
        }
        // Same hash → do nothing, status stays DONE
    }

    /** Marks a chunk as successfully uploaded. */
    public void markDone(String filePath, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE chunks SET status='DONE' WHERE file_path=? AND chunk_index=?")) {
            ps.setString(1, filePath);
            ps.setInt(2, chunkIndex);
            ps.executeUpdate();
        }
    }

    /** Returns the status of a chunk, or null if not found. */
    public Status getStatus(String filePath, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM chunks WHERE file_path=? AND chunk_index=?")) {
            ps.setString(1, filePath);
            ps.setInt(2, chunkIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Status.valueOf(rs.getString("status"));
                return null;
            }
        }
    }

    /** Returns the stored hash for a chunk, or null if not found. */
    public String getHash(String filePath, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hash FROM chunks WHERE file_path=? AND chunk_index=?")) {
            ps.setString(1, filePath);
            ps.setInt(2, chunkIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("hash");
                return null;
            }
        }
    }

    /** Returns how many chunks are still pending upload for a given file. */
    public int countPending(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM chunks WHERE file_path=? AND status='PENDING'")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Removes all chunk records for a deleted file. */
    public void removeFile(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM chunks WHERE file_path=?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}