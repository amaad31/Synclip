package dev.synclip;

import io.minio.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Uploads file chunks to MinIO/S3 in parallel using a ThreadPoolExecutor.
 * Uses RetryPolicy for automatic retry on network failure.
 *
 * Flow:
 *   1. Get all PENDING chunks from SyncManifest
 *   2. Submit each chunk as a separate upload task to the thread pool
 *   3. On success → markDone() in SyncManifest
 *   4. On failure → RetryPolicy retries up to 3 times
 */
public class UploadWorker implements AutoCloseable {

    private static final int DEFAULT_THREADS = 4;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 200;

    private final MinioClient minioClient;
    private final String bucketName;
    private final SyncManifest manifest;
    private final RetryPolicy retryPolicy;
    private final ExecutorService threadPool;

    public UploadWorker(MinioClient minioClient,
                        String bucketName,
                        SyncManifest manifest) {
        this(minioClient, bucketName, manifest, DEFAULT_THREADS);
    }

    public UploadWorker(MinioClient minioClient,
                        String bucketName,
                        SyncManifest manifest,
                        int threads) {
        this.minioClient = minioClient;
        this.bucketName  = bucketName;
        this.manifest    = manifest;
        this.retryPolicy = new RetryPolicy(MAX_RETRIES, RETRY_DELAY_MS);
        this.threadPool  = Executors.newFixedThreadPool(threads);
    }

    /**
     * Uploads all chunks of a file in parallel.
     * Waits until all uploads are complete before returning.
     *
     * @param filePath  logical path used as key in SyncManifest
     * @param chunks    list of chunks from Chunker.split()
     */
    public void uploadAll(String filePath, List<Chunker.Chunk> chunks)
            throws InterruptedException {

        // Filter only PENDING chunks — skip already uploaded ones
        List<Chunker.Chunk> pending = new ArrayList<>();
        for (Chunker.Chunk chunk : chunks) {
            try {
                SyncManifest.Status status = manifest.getStatus(filePath, chunk.index());
                if (status != SyncManifest.Status.DONE) {
                    pending.add(chunk);
                }
            } catch (Exception e) {
                pending.add(chunk); // if unsure, upload
            }
        }

        if (pending.isEmpty()) return; // nothing to do — full delta sync hit

        // Submit each chunk as a parallel task
        List<Future<Void>> futures = new ArrayList<>();
        for (Chunker.Chunk chunk : pending) {
            futures.add(threadPool.submit(() -> {
                uploadChunk(filePath, chunk);
                return null;
            }));
        }

        // Wait for all uploads to finish
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Chunk upload failed: " + e.getCause().getMessage(), e);
            }
        }
    }

    /**
     * Uploads a single chunk with retry logic.
     * Object key format: "deviceId/filePath/chunk_N"
     */
    private void uploadChunk(String filePath, Chunker.Chunk chunk) throws Exception {
        String objectKey = manifest.getDeviceId() + "/"
                + filePath + "/chunk_" + chunk.index();

        retryPolicy.execute(() -> {
            try {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(chunk.data()), chunk.size(), -1)
                        .contentType("application/octet-stream")
                        .build()
                );
                manifest.markDone(filePath, chunk.index());
            } catch (Exception e) {
                throw new IOException("MinIO upload failed for chunk " + chunk.index(), e);
            }
        });
    }

    /** Ensures the bucket exists — creates it if not. */
    public void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @Override
    public void close() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}