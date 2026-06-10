package com.shopee.monolith.modules.search.event;

import com.shopee.monolith.modules.product.entity.ProductStatus;
import com.shopee.monolith.modules.product.event.ProductCatalogSnapshotEvent;
import com.shopee.monolith.modules.product.event.ProductListingStatusChangedEvent;
import com.shopee.monolith.modules.search.service.EmbeddingIndexService;
import com.shopee.monolith.modules.search.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ProductCatalogEventListenerTest {

    @Mock
    private SearchIndexService searchIndexService;

    @Mock
    private EmbeddingIndexService embeddingIndexService;

    @InjectMocks
    private ProductCatalogEventListener listener;

    // ===================== ProductCatalogSnapshotEvent =====================

    @Test
    void snapshotEventWhenActiveShouldUpsertBothIndexes() {
        UUID productId = UUID.randomUUID();
        ProductCatalogSnapshotEvent event = buildSnapshotEvent(productId, ProductStatus.ACTIVE);

        listener.onProductCatalogSnapshot(event);

        verify(searchIndexService).upsertDocument(event);
        verify(embeddingIndexService).indexProduct(event);
        verifyNoMoreInteractions(searchIndexService, embeddingIndexService);
    }

    @Test
    void snapshotEventWhenInactiveShouldDeleteFromBothIndexes() {
        UUID productId = UUID.randomUUID();
        ProductCatalogSnapshotEvent event = buildSnapshotEvent(productId, ProductStatus.INACTIVE);

        listener.onProductCatalogSnapshot(event);

        verify(searchIndexService).deleteDocument(productId);
        verify(embeddingIndexService).removeProduct(productId);
        verifyNoMoreInteractions(searchIndexService, embeddingIndexService);
    }

    @Test
    void snapshotEventWhenDeletedShouldDeleteFromBothIndexes() {
        UUID productId = UUID.randomUUID();
        ProductCatalogSnapshotEvent event = buildSnapshotEvent(productId, ProductStatus.DELETED);

        listener.onProductCatalogSnapshot(event);

        verify(searchIndexService).deleteDocument(productId);
        verify(embeddingIndexService).removeProduct(productId);
    }

    @Test
    void snapshotEventWhenDraftShouldDeleteFromBothIndexes() {
        UUID productId = UUID.randomUUID();
        ProductCatalogSnapshotEvent event = buildSnapshotEvent(productId, ProductStatus.DRAFT);

        listener.onProductCatalogSnapshot(event);

        verify(searchIndexService).deleteDocument(productId);
        verify(embeddingIndexService).removeProduct(productId);
    }

    // ===================== ProductListingStatusChangedEvent =====================

    @Test
    void listingStatusChangedWhenInactiveShouldDeleteFromBothIndexes() {
        UUID productId = UUID.randomUUID();
        ProductListingStatusChangedEvent event = new ProductListingStatusChangedEvent(productId, UUID.randomUUID(), ProductStatus.INACTIVE);

        listener.onProductListingStatusChanged(event);

        verify(searchIndexService).deleteDocument(productId);
        verify(embeddingIndexService).removeProduct(productId);
        verifyNoMoreInteractions(searchIndexService, embeddingIndexService);
    }

    @Test
    void listingStatusChangedWhenActiveShouldDoNothing() {
        UUID productId = UUID.randomUUID();
        ProductListingStatusChangedEvent event = new ProductListingStatusChangedEvent(productId, UUID.randomUUID(), ProductStatus.ACTIVE);

        listener.onProductListingStatusChanged(event);

        verifyNoMoreInteractions(searchIndexService, embeddingIndexService);
    }

    // ===================== Helpers =====================

    private ProductCatalogSnapshotEvent buildSnapshotEvent(UUID productId, ProductStatus status) {
        return new ProductCatalogSnapshotEvent(
                productId,
                UUID.randomUUID(),
                status,
                "Test Product",
                "Description",
                "Electronics > Phones",
                "Brand",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Test Shop",
                null,
                true,
                null,
                null
        );
    }
}
