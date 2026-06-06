package com.shopee.monolith.modules.order.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import com.shopee.monolith.modules.order.model.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "checkout_session_id", nullable = false)
    private UUID checkoutSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_street", nullable = false)
    private String shippingStreet;

    @Column(name = "shipping_city", nullable = false)
    private String shippingCity;

    @Version
    @Column(name = "version", nullable = false)
    @lombok.Builder.Default
    private int version = 0;

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }
}
