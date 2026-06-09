package com.shopee.monolith.modules.media.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;

class R2StorageServiceTest {

    @Test
    void initializeWhenRequiredConfigMissingShouldFailFast() {
        R2StorageService storageService = new R2StorageService();
        ReflectionTestUtils.setField(storageService, "endpoint", "");
        ReflectionTestUtils.setField(storageService, "accessKeyId", "access-key");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "secret-key");
        ReflectionTestUtils.setField(storageService, "bucket", "bucket");
        ReflectionTestUtils.setField(storageService, "publicBaseUrl", "https://cdn.example.com");

        assertThrows(IllegalStateException.class, storageService::initialize);
    }
}
