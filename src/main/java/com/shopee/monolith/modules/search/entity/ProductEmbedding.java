package com.shopee.monolith.modules.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata record for pgvector product embeddings.
 *
 * <p>The {@code embedding} column (vector(768)) is managed exclusively through
 * native SQL in {@link com.shopee.monolith.modules.search.repository.ProductEmbeddingRepository}
 * because Hibernate has no built-in type mapping for the PostgreSQL {@code vector} type.
 * This entity is used only for {@code deleteById} and schema-presence validation.
 */
@Entity
@Table(name = "product_embeddings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductEmbedding {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    // NOTE: The `embedding vector(768)` column is NOT mapped here.
    // Hibernate schema validation only checks that mapped columns exist in the DB,
    // so unmapped DB columns (like `embedding`) do not cause validation failures.
    // All embedding read/write operations use native SQL via the repository.
}
