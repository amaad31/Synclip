package dev.synclip;

import java.nio.file.Path;
import java.sql.*;

/**
 * Tracks which chunks have been uploaded per device using SQLite.
 * Each device has its own chunk records, two Macs never overwrite each other.
 *
 * Schema:
 *   chunks(device_id, file_path, chunk_index, hash, status)
 *   status = PENDING | DONE
 */
public class SyncManifest implements AutoCloseable {

    public enum Status { PENDING, DONE }

    private final Connection conn;
    private final String deviceId;

    public SyncManifest(Path dbPath, String deviceId) throws SQLException {
        this.deviceId = deviceId;
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        initSchema();
    }

    /** In-memory database for tests. */
    public SyncManifest(String deviceId) throws SQLException {
        this.deviceId = deviceId;
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    device_id   TEXT    NOT NULL,
                    file_path   TEXT    NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    hash        TEXT    NOT NULL,
                    status      TEXT    NOT NULL DEFAULT 'PENDING',
                    PRIMARY KEY (device_id, file_path, chunk_index)
                )
            """);
        }
    }

    /**
     * Inserts or updates a chunk record for this device.
     * Same hash -> status stays DONE (delta sync).
     * Changed hash -> status resets to PENDING (needs re-upload).
     */
    public void upsert(String filePath, int chunkIndex, String hash) throws SQLException {
        String existing = getHash(filePath, chunkIndex);

        if (existing == null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO chunks(device_id, file_path, chunk_index, hash, status) VALUES(?,?,?,?,'PENDING')")) {
                ps.setString(1, deviceId);
                ps.setString(2, filePath);
                ps.setInt(3, chunkIndex);
                ps.setString(4, hash);
                ps.executeUpdate();
            }
        } else if (!existing.equals(hash)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE chunks SET hash=?, status='PENDING' WHERE device_id=? AND file_path=? AND chunk_index=?")) {
                ps.setString(1, hash);
                ps.setString(2, deviceId);
                ps.setString(3, filePath);
                ps.setInt(4, chunkIndex);
                ps.executeUpdate();
            }
        }
    }

    /** Marks a chunk as successfully uploaded. */
    public void markDone(String filePath, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE chunks SET status='DONE' WHERE device_id=? AND file_path=? AND chunk_index=?")) {
            ps.setString(1, deviceId);
            ps.setString(2, filePath);
            ps.setInt(3, chunkIndex);
            ps.executeUpdate();
        }
    }

    /** Returns the status of a chunk, or null if not found. */
    public Status getStatus(String filePath, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM chunks WHERE device_id=? AND file_path=? AND chunk_index=?")) {
            ps.setString(1, deviceId);
            ps.setString(2, filePath);
            ps.setInt(3, chunkIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Status.valueOf(rs.getString("status"));
                return null;
            }
        }
    }

    /** Returns the stored hash for a chunk, or null if not found. */
    public String getHash(String filePath, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hash FROM chunks WHERE device_id=? AND file_path=? AND chunk_index=?")) {
            ps.setString(1, deviceId);
            ps.setString(2, filePath);
            ps.setInt(3, chunkIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("hash");
                return null;
            }
        }
    }

    /** Returns how many chunks are still pending upload for a given file. */
    public int countPending(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM chunks WHERE device_id=? AND file_path=? AND status='PENDING'")) {
            ps.setString(1, deviceId);
            ps.setString(2, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Removes all chunk records for a deleted file on this device. */
    public void removeFile(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM chunks WHERE device_id=? AND file_path=?")) {
            ps.setString(1, deviceId);
            ps.setString(2, filePath);
            ps.executeUpdate();
        }
    }

    public String getDeviceId() { return deviceId; }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}