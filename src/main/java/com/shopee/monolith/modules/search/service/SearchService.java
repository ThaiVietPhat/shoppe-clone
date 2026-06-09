package com.shopee.monolith.modules.search.service;

import com.shopee.monolith.modules.search.dto.SearchRequest;
import com.shopee.monolith.modules.search.dto.SearchResponse;

/**
 * Keyword and facet product search with graceful Elasticsearch fallback.
 */
public interface SearchService {

    /**
     * Executes a keyword/facet search.
     * Falls back to PostgreSQL LIKE query when Elasticsearch is unavailable,
     * returning {@code degraded=true} in the response.
     */
    SearchResponse search(SearchRequest request);
}
