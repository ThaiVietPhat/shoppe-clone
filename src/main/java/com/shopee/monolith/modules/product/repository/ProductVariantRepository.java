package com.shopee.monolith.modules.product.repository;

import com.shopee.monolith.modules.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findAllByProductId(UUID productId);

    List<ProductVariant> findAllByProductIdIn(Collection<UUID> productIds);

    List<ProductVariant> findAllByProductIdAndActive(UUID productId, boolean active);

    @Query("SELECT v FROM ProductVariant v JOIN Product p ON p.id = v.productId "
            + "WHERE v.id = :variantId AND v.active = true AND v.price > 0 AND p.status = :status")
    Optional<ProductVariant> findActiveByIdAndProductStatus(
            @Param("variantId") UUID variantId,
            @Param("status") com.shopee.monolith.modules.product.entity.ProductStatus status);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.productId = :productId AND v.active = true AND v.price > :minPrice")
    long countActiveVariantsWithPriceAbove(@Param("productId") UUID productId, @Param("minPrice") BigDecimal minPrice);

    @Query("SELECT MIN(v.price) FROM ProductVariant v "
            + "WHERE v.productId = :productId AND v.active = true AND v.price > 0")
    Optional<BigDecimal> findMinPriceByProductId(@Param("productId") UUID productId);

    @Query("SELECT MAX(v.price) FROM ProductVariant v "
            + "WHERE v.productId = :productId AND v.active = true AND v.price > 0")
    Optional<BigDecimal> findMaxPriceByProductId(@Param("productId") UUID productId);
}
