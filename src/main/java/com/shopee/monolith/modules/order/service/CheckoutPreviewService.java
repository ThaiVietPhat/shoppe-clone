package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.order.dto.request.CheckoutPreviewRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutPreviewResponse;

import java.util.UUID;

public interface CheckoutPreviewService {
    CheckoutPreviewResponse preview(UUID buyerId, CheckoutPreviewRequest request);
}
