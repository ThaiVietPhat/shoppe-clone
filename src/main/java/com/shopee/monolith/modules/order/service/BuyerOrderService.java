package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.order.dto.response.BuyerOrderDetailResponse;
import com.shopee.monolith.modules.order.dto.response.BuyerOrderSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BuyerOrderService {

    PagedResponse<BuyerOrderSummaryResponse> listOrders(UUID buyerId, Pageable pageable);

    BuyerOrderDetailResponse getOrderDetail(UUID buyerId, UUID orderId);

    /**
     * Cancels a PENDING_PAYMENT order and releases its RESERVED inventory atomically.
     * Any other state is rejected with ORDER_CANNOT_BE_CANCELLED.
     */
    void cancelOrder(UUID buyerId, UUID orderId);
}
