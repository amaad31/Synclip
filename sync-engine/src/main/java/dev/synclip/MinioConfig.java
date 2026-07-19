package dev.synclip;

import io.minio.MinioClient;

/**
 * Builds and holds the MinIO client configuration.
 * All connection details in one place — easy to change for different environments.
 *
 * Local dev:   endpoint = http://localhost:9000
 * Raspberry Pi: endpoint = http://192.168.1.100:9000
 * Production:  endpoint = https://your-domain.com
 */
public class MinioConfig {

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;

    public MinioConfig(String endpoint, String accessKey,
                       String secretKey, String bucketName) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
    }

    /** Builds a ready-to-use MinioClient from this config. */
    public MinioClient buildClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public String getBucketName() { return bucketName; }
    public String getEndpoint()   { return endpoint; }

    /** Default local development config — MinIO running via Docker. */
    public static MinioConfig local() {
        return new MinioConfig(
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                "synclip"
        );
    }
}