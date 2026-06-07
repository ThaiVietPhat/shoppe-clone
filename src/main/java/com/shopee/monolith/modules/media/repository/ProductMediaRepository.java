package com.shopee.monolith.modules.media.repository;

import com.shopee.monolith.modules.media.entity.ProductMedia;
import com.shopee.monolith.modules.media.entity.ProductMediaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, ProductMediaId> {

    List<ProductMedia> findAllByIdProductIdOrderBySortOrder(UUID productId);

    Optional<ProductMedia> findByIdProductIdAndIdMediaId(UUID productId, UUID mediaId);

    boolean existsByIdProductIdAndCoverTrue(UUID productId);

    @Modifying
    @Query("UPDATE ProductMedia pm SET pm.cover = false WHERE pm.id.productId = :productId AND pm.cover = true")
    void clearCoverByProductId(@Param("productId") UUID productId);

    @Modifying
    @Query("DELETE FROM ProductMedia pm WHERE pm.id.productId = :productId AND pm.id.mediaId = :mediaId")
    void deleteByProductIdAndMediaId(@Param("productId") UUID productId, @Param("mediaId") UUID mediaId);

    @Modifying
    @Query("DELETE FROM ProductMedia pm WHERE pm.id.productId = :productId")
    void deleteAllByProductId(@Param("productId") UUID productId);

    List<ProductMedia> findAllByIdProductIdInOrderBySortOrder(List<UUID> productIds);
}
