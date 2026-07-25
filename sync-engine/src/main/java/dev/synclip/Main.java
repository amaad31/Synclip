package dev.synclip;

import io.minio.MinioClient;
import java.nio.file.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("===========================================");
        System.out.println("  Synclip — Encrypted File Sync & Clipboard");
        System.out.println("===========================================");

        SynclipConfig config   = new SynclipConfig();
        DeviceIdentity identity = new DeviceIdentity();
        System.out.println("[Synclip] Device ID: " + identity.getDeviceId());

        Path dbPath      = config.getConfigDir().resolve("manifest.db");
        SyncManifest manifest = new SyncManifest(dbPath, identity.getDeviceId());

        MinioConfig minioConfig = new MinioConfig(
                config.get("minio.endpoint", "http://localhost:9000"),
                config.get("minio.accessKey", "minioadmin"),
                config.get("minio.secretKey", "minioadmin"),
                config.get("minio.bucket",    "synclip"));
        MinioClient minioClient = minioConfig.buildClient();

        UploadWorker uploadWorker = new UploadWorker(
                minioClient, minioConfig.getBucketName(), manifest);
        uploadWorker.ensureBucket();
        System.out.println("[Synclip] Connected to MinIO: " + config.get("minio.endpoint"));

        Path keyFile = config.getConfigDir().resolve("synclip.key");
        javax.crypto.SecretKey key;
        if (Files.exists(keyFile)) {
            key = CryptoHelper.keyFromBytes(Files.readAllBytes(keyFile));
            System.out.println("[Synclip] Encryption key loaded.");
        } else {
            key = CryptoHelper.generateKey();
            Files.write(keyFile, key.getEncoded());
            System.out.println("[Synclip] New key generated: " + keyFile);
        }
        CryptoHelper crypto = new CryptoHelper(key);

        Chunker chunker = new Chunker();
        List<Path> watchFolders = config.getWatchFolders();
        for (Path folder : watchFolders) {
            Files.createDirectories(folder);
            System.out.println("[Synclip] Watching: " + folder);
        }

        SyncEngine syncEngine = new SyncEngine(chunker, manifest, uploadWorker, watchFolders);
        syncEngine.start();

        ClipboardManager clipboardManager = new ClipboardManager();
        ClipboardReceiver receiver = new ClipboardReceiver(
                config.getClipboardPort(), crypto, clipboardManager);
        receiver.start();

        PeerDiscovery peerDiscovery = new PeerDiscovery(
                identity.getDeviceId(), config.getClipboardPort(),
                peer -> System.out.println("[Synclip] Peer discovered: " + peer));
        peerDiscovery.start();

        ClipboardSender sender = new ClipboardSender(crypto, clipboardManager);
        HotkeyListener hotkey = new HotkeyListener(() -> {
            Collection<PeerDiscovery.PeerInfo> peers = peerDiscovery.getKnownPeers();
            if (peers.isEmpty()) { System.out.println("[Synclip] No peers found."); return; }
            for (PeerDiscovery.PeerInfo peer : peers) {
                try {
                    sender.sendToPeer(peer.ipAddress(), peer.port());
                    System.out.println("[Synclip] Clipboard sent to: " + peer);
                } catch (Exception e) {
                    System.err.println("[Synclip] Failed: " + e.getMessage());
                }
            }
        });
        hotkey.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Synclip] Shutting down...");
            syncEngine.close();
            receiver.close();
            peerDiscovery.close();
            hotkey.close();
            try { manifest.close(); } catch (Exception ignored) {}
            System.out.println("[Synclip] Goodbye!");
        }));

        System.out.println("===========================================");
        System.out.println("  Synclip is running! Press Ctrl+C to stop.");
        System.out.println("===========================================");
        Thread.currentThread().join();
    }
}