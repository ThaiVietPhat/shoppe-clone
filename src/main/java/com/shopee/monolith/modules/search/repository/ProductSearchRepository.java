package com.shopee.monolith.modules.search.repository;

import com.shopee.monolith.modules.search.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    // Custom queries are handled via ElasticsearchOperations in SearchServiceImpl
}
