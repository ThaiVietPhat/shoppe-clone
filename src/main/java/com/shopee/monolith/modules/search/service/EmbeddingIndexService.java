package com.shopee.monolith.modules.search.service;

import com.shopee.monolith.modules.product.event.ProductCatalogSnapshotEvent;

import java.util.UUID;

/**
 * Manages pgvector embedding lifecycle for the product catalog.
 * Embedding input is built from public catalog fields only — never PII.
 */
public interface EmbeddingIndexService {

    /**
     * Generates and upserts a pgvector embedding for the product described in the snapshot.
     * If the AI provider is unavailable, logs a warning and returns gracefully
     * — the publication log will trigger a replay.
     */
    void indexProduct(ProductCatalogSnapshotEvent event);

    /**
     * Removes the embedding for the given product.
     * Idempotent — safe to call even if no embedding exists.
     */
    void removeProduct(UUID productId);
}
