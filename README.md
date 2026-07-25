# Synclip

> Sync your files and clipboard across all your devices. Fast, private, no subscription.

## What is Synclip?

Synclip is a lightweight encrypted file sync daemon and P2P clipboard sharing tool. It runs silently in the background, watches your folders for changes, and syncs only what changed — no full re-uploads, no cloud subscriptions, no one else has access to your data.

## Architecture

```
MacBook / Windows PC
├── FileWatcher      — detects file changes (FSEvents / inotify)
├── SyncEngine       — orchestrates the full pipeline
├── DeltaSync        — only uploads changed chunks
├── UploadWorker     — parallel chunk uploads via ThreadPoolExecutor
├── ClipboardSender  — encrypts + sends clipboard via TCP
└── ClipboardReceiver— receives + decrypts clipboard from peers

MinIO Server (Raspberry Pi / Docker)
└── Stores AES-256-GCM encrypted chunks
    Nobody can read your data without your key
```

## Quick Start

### Run with Docker

```bash
git clone https://github.com/amaad31/synclip.git
cd synclip
docker-compose up
```

MinIO Console: http://localhost:9001 (minioadmin / minioadmin)

### Run locally

```bash
mvn package -DskipTests
java -jar sync-engine/target/sync-engine-1.0-SNAPSHOT.jar
```

On first start, Synclip creates `~/.synclip/config.properties` with default settings.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Build | Maven Multi-Module |
| Storage | MinIO (S3-compatible) |
| Database | SQLite |
| Encryption | AES-256-GCM |
| Networking | TCP Sockets, UDP Broadcast |
| File Watching | Java WatchService |
| Parallelism | ThreadPoolExecutor |
| Testing | JUnit 5, 120+ tests |
| CI | GitHub Actions |
| Deployment | Docker + Docker Compose |

## License

MIT