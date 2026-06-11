package com.shopee.monolith.modules.order.model;

/**
 * Seller-side fulfillment state machine: READY_TO_SHIP -> SHIPPED -> DELIVERED.
 * Null on the order until payment is confirmed.
 */
public enum FulfillmentStatus {
    READY_TO_SHIP,
    SHIPPED,
    DELIVERED
}
