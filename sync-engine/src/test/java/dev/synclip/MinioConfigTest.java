package dev.synclip;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MinioConfigTest {

    @Test
    @DisplayName("local() config has correct default values")
    void localConfigHasCorrectDefaults() {
        MinioConfig config = MinioConfig.local();
        assertEquals("http://localhost:9000", config.getEndpoint());
        assertEquals("synclip", config.getBucketName());
    }

    @Test
    @DisplayName("buildClient() returns a MinioClient")
    void buildClientReturnsMinioClient() {
        MinioConfig config = MinioConfig.local();
        assertNotNull(config.buildClient());
    }

    @Test
    @DisplayName("custom config stores all values correctly")
    void customConfigStoresValues() {
        MinioConfig config = new MinioConfig(
                "http://192.168.1.100:9000",
                "user", "pass", "mybucket");
        assertEquals("http://192.168.1.100:9000", config.getEndpoint());
        assertEquals("mybucket", config.getBucketName());
    }
}