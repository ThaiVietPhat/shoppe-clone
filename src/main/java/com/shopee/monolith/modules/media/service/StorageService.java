package com.shopee.monolith.modules.media.service;

public interface StorageService {

    /**
     * Store raw bytes under the given objectKey.
     *
     * @param bytes       file content bytes
     * @param objectKey   pre-generated unique key (never derived from client filename)
     * @param contentType MIME type already validated by caller
     * @return objectKey (same value, for chaining convenience)
     */
    String store(byte[] bytes, String objectKey, String contentType);

    /**
     * Build a publicly accessible URL for the given objectKey.
     * For local storage returns a localhost URL; for R2 returns CDN URL.
     */
    String getPublicUrl(String objectKey);

    byte[] load(String objectKey);

    /**
     * Delete the object identified by objectKey.
     * Implementation must be idempotent — missing key should not throw.
     */
    void delete(String objectKey);
}
