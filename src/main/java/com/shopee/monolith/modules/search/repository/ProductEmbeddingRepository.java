package com.shopee.monolith.modules.search.repository;

import com.shopee.monolith.modules.search.entity.ProductEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, UUID> {

    /**
     * Upserts a product embedding using PostgreSQL ON CONFLICT.
     * {@code embedding} must be a valid pgvector literal string, e.g. {@code "[0.1,0.2,...,0.768]"}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO product_embeddings (product_id, embedding, model_version, indexed_at)
            VALUES (:productId, CAST(:embedding AS vector), :modelVersion, :indexedAt)
            ON CONFLICT (product_id)
            DO UPDATE SET
                embedding     = EXCLUDED.embedding,
                model_version = EXCLUDED.model_version,
                indexed_at    = EXCLUDED.indexed_at
            """, nativeQuery = true)
    void upsert(
            @Param("productId") UUID productId,
            @Param("embedding") String embedding,
            @Param("modelVersion") String modelVersion,
            @Param("indexedAt") Instant indexedAt);

    /**
     * Cosine similarity nearest-neighbour search using the IVFFlat index.
     * Returns product_id values cast to {@code text} so Spring Data JPA returns
     * them as {@code String} without UUID-type ambiguity across JDBC drivers.
     * Caller converts to {@link UUID} before use.
     *
     * @param queryVector pgvector literal for the query embedding, e.g. {@code "[0.1,...,0.768]"}
     * @param limit       maximum number of candidates to return
     */
    @Query(value = """
            SELECT product_id::text
            FROM product_embeddings
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findSimilarProductIdStrings(
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);
}
