package com.shopee.monolith.modules.inventory.entity;

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

import java.util.UUID;

/**
 * Append-only audit ledger row for every stock mutation.
 * Written in the same transaction as the inventory mutation it records.
 */
@Entity
@Table(name = "inventory_movements")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryMovement extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private InventoryMovementType movementType;

    /** Positive magnitude for RESERVE/CONFIRM/RELEASE/INITIAL; signed delta for STOCK_UPDATE. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "available_stock_after", nullable = false)
    private int availableStockAfter;

    @Column(name = "reserved_stock_after", nullable = false)
    private int reservedStockAfter;
}
