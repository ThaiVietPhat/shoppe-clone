package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.user.dto.response.AddressResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ShippingFeeEstimator {
    BigDecimal estimateFee(UUID shopId, List<CartSnapshotItem> items, AddressResponse address);
}
