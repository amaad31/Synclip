package dev.synclip;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SynclipConfig {

    private static final Path CONFIG_DIR  = Path.of(
            System.getProperty("user.home"), ".synclip");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    private final Properties props = new Properties();

    public SynclipConfig() throws IOException {
        Files.createDirectories(CONFIG_DIR);
        if (!Files.exists(CONFIG_FILE)) createDefault();
        try (var in = Files.newInputStream(CONFIG_FILE)) { props.load(in); }
    }

    private void createDefault() throws IOException {
        String defaultConfig = """
                minio.endpoint=http://localhost:9000
                minio.accessKey=minioadmin
                minio.secretKey=minioadmin
                minio.bucket=synclip
                watch.folders=%s/Desktop/synclip-watch
                clipboard.port=9091
                """.formatted(System.getProperty("user.home"));
        Files.writeString(CONFIG_FILE, defaultConfig);
        System.out.println("[Synclip] Default config created at: " + CONFIG_FILE);
    }

    public String get(String key)                  { return props.getProperty(key); }
    public String get(String key, String fallback) { return props.getProperty(key, fallback); }

    public List<Path> getWatchFolders() {
        String raw = get("watch.folders", "");
        List<Path> folders = new ArrayList<>();
        for (String p : raw.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) folders.add(Path.of(trimmed));
        }
        return folders;
    }

    public int getClipboardPort() {
        return Integer.parseInt(get("clipboard.port", "9091"));
    }

    public Path getConfigDir() { return CONFIG_DIR; }
}