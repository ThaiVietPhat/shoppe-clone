package com.shopee.monolith.modules.product.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @lombok.Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "seller_sku", length = 100)
    private String sellerSku;

    @Column(name = "attributes", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @lombok.Builder.Default
    private Map<String, Object> attributes = Map.of();

    @Column(name = "min_price", precision = 15, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 15, scale = 2)
    private BigDecimal maxPrice;

    // ===================== Domain Methods =====================

    public void update(UUID categoryId, String name, String description,
                       String brand, String sellerSku, Map<String, Object> attributes) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.brand = brand;
        this.sellerSku = sellerSku;
        this.attributes = attributes;
    }

    /**
     * Transition DRAFT or INACTIVE → ACTIVE.
     * Invariant check (variant count, price) is done in the service before calling this.
     */
    public void publish() {
        if (this.status == ProductStatus.DELETED) {
            throw new IllegalStateException("Cannot publish a deleted product");
        }
        this.status = ProductStatus.ACTIVE;
    }

    /**
     * Transition ACTIVE → INACTIVE.
     */
    public void unpublish() {
        if (this.status == ProductStatus.ACTIVE) {
            this.status = ProductStatus.INACTIVE;
        }
    }

    /**
     * Soft delete — sets status to DELETED. Not reversible without direct DB update.
     */
    public void softDelete() {
        this.status = ProductStatus.DELETED;
    }

    /**
     * Recompute min/max price range from active variant prices.
     */
    public void recomputePriceRange(BigDecimal min, BigDecimal max) {
        this.minPrice = min;
        this.maxPrice = max;
    }

    public boolean isPublishable() {
        return this.status == ProductStatus.DRAFT || this.status == ProductStatus.INACTIVE;
    }
}
