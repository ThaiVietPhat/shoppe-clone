package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.order.dto.response.SellerDashboardResponse;
import com.shopee.monolith.modules.order.dto.response.SellerOrderDetailResponse;
import com.shopee.monolith.modules.order.dto.response.SellerOrderSummaryResponse;
import com.shopee.monolith.modules.order.model.FulfillmentStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SellerOrderService {

    PagedResponse<SellerOrderSummaryResponse> listOrders(
            UUID sellerId, FulfillmentStatus fulfillmentStatus, Pageable pageable);

    SellerOrderDetailResponse getOrderDetail(UUID sellerId, UUID orderId);

    SellerOrderDetailResponse shipOrder(UUID sellerId, UUID orderId);

    SellerOrderDetailResponse deliverOrder(UUID sellerId, UUID orderId);

    SellerDashboardResponse getDashboard(UUID sellerId);
}
