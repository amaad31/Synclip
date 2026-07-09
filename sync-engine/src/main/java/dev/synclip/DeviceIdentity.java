package dev.synclip;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * Generates and persists a unique device ID.
 * Created once on first start, stored in ~/.synclip/device.id
 * Every device gets a different ID, the server uses it to separate uploads.
 */
public class DeviceIdentity {

    private static final Path ID_FILE = Path.of(
            System.getProperty("user.home"), ".synclip", "device.id");

    private final String deviceId;

    public DeviceIdentity() throws IOException {
        this.deviceId = loadOrCreate();
    }

    /** Returns this device's unique ID, e.g. "mac-a3f9b2c1" */
    public String getDeviceId() {
        return deviceId;
    }

    private String loadOrCreate() throws IOException {
        if (Files.exists(ID_FILE)) {
            return Files.readString(ID_FILE).trim();
        }

        // First start, generate a new ID
        String newId = generateId();
        Files.createDirectories(ID_FILE.getParent());
        Files.writeString(ID_FILE, newId);
        return newId;
    }

    private String generateId() {
        String os = System.getProperty("os.name")
                .toLowerCase()
                .replaceAll("[^a-z]", "")
                .substring(0, Math.min(3, 3)); // "mac", "win", "lin"
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return os + "-" + unique; // e.g. "mac-a3f9b2c1"
    }
}