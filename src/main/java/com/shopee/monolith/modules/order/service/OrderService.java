package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;

import java.util.UUID;

public interface OrderService {
    CheckoutResponse checkout(UUID buyerId, CheckoutRequest request, String idempotencyKey);
}
