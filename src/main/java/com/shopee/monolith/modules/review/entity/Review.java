package com.shopee.monolith.modules.review.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 2000)
    private String comment;

    public void update(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }
}
