package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.order.config.MockShippingProperties;
import com.shopee.monolith.modules.user.dto.response.AddressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlatRateShippingFeeEstimator implements ShippingFeeEstimator {

    private final MockShippingProperties mockShippingProperties;

    @Override
    public BigDecimal estimateFee(UUID shopId, List<CartSnapshotItem> items, AddressResponse address) {
        return mockShippingProperties.getFlatFeePerShop();
    }
}
