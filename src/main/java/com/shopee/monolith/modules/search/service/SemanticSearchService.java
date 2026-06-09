package com.shopee.monolith.modules.search.service;

import com.shopee.monolith.modules.search.dto.SearchResponse;

/**
 * Semantic product search using pgvector cosine similarity.
 * Degrades gracefully when the AI embedding provider is unavailable.
 */
public interface SemanticSearchService {

    /**
     * Embeds {@code query} and returns the closest matching ACTIVE products.
     * Returns {@code degraded=true} when the embedding provider is unavailable.
     */
    SearchResponse search(String query, int page, int size);
}
