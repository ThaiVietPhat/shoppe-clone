package com.shopee.monolith.modules.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_media")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMedia {

    @EmbeddedId
    private ProductMediaId id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    public void setCover(boolean cover) {
        this.cover = cover;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
