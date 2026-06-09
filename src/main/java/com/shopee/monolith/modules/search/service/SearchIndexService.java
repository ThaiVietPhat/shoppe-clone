package com.shopee.monolith.modules.search.service;

import com.shopee.monolith.modules.product.event.ProductCatalogSnapshotEvent;

import java.util.UUID;

/**
 * Manages Elasticsearch document lifecycle for the product catalog.
 */
public interface SearchIndexService {

    /** Upserts a product document in Elasticsearch from a catalog snapshot event. */
    void upsertDocument(ProductCatalogSnapshotEvent event);

    /** Removes a product document from Elasticsearch. Idempotent if already absent. */
    void deleteDocument(UUID productId);
}
