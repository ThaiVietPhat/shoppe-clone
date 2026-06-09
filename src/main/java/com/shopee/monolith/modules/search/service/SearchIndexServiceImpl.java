package com.shopee.monolith.modules.search.service;

import com.shopee.monolith.modules.product.event.ProductCatalogSnapshotEvent;
import com.shopee.monolith.modules.search.document.ProductDocument;
import com.shopee.monolith.modules.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexServiceImpl implements SearchIndexService {

    private final ProductSearchRepository productSearchRepository;

    @Override
    public void upsertDocument(ProductCatalogSnapshotEvent event) {
        try {
            ProductDocument doc = ProductDocument.builder()
                    .productId(event.productId().toString())
                    .name(event.name())
                    .description(event.description())
                    .categoryPath(event.categoryPath())
                    .brand(event.brand())
                    .attributes(event.attributes())
                    .minPrice(event.minPrice())
                    .maxPrice(event.maxPrice())
                    .shopId(event.shopId() != null ? event.shopId().toString() : null)
                    .shopName(event.shopName())
                    .shopRating(event.shopRating())
                    .coverImageUrl(event.coverImageUrl())
                    .coverMediaId(event.coverMediaId() != null ? event.coverMediaId().toString() : null)
                    .status(event.status() != null ? event.status().name() : null)
                    .createdAt(null)
                    .build();
            productSearchRepository.save(doc);
            log.debug("ES upsert OK productId={}", event.productId());
        } catch (Exception ex) {
            log.warn("ES upsert failed productId={} — will retry via publication replay", event.productId(), ex);
        }
    }

    @Override
    public void deleteDocument(UUID productId) {
        try {
            productSearchRepository.deleteById(productId.toString());
            log.debug("ES delete OK productId={}", productId);
        } catch (Exception ex) {
            log.warn("ES delete failed productId={} — will retry via publication replay", productId, ex);
        }
    }
}
