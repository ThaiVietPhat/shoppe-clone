package com.shopee.monolith.modules.order.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.order.dto.response.SellerDashboardResponse;
import com.shopee.monolith.modules.order.dto.response.SellerOrderDetailResponse;
import com.shopee.monolith.modules.order.dto.response.SellerOrderSummaryResponse;
import com.shopee.monolith.modules.order.model.FulfillmentStatus;
import com.shopee.monolith.modules.order.service.SellerOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@Tag(name = "Seller Orders", description = "Seller-scoped order dashboard, detail and fulfillment APIs")
public class SellerOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SellerOrderService sellerOrderService;

    @Operation(
            summary = "List orders of the current seller's shop",
            description = "Paged list of orders scoped to the authenticated seller's shop, newest first. "
                    + "Optionally filtered by fulfillment status.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Orders returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Caller does not own a shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @GetMapping("/orders")
    public ApiResponse<PagedResponse<SellerOrderSummaryResponse>> listOrders(
            @Parameter(description = "Optional fulfillment status filter", example = "READY_TO_SHIP")
            @RequestParam(required = false) FulfillmentStatus fulfillmentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.success(sellerOrderService.listOrders(
                claims.userId(), fulfillmentStatus, PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @Operation(
            summary = "Get seller order detail",
            description = "Order detail with fulfillment-safe customer snapshot, item snapshots and payment info. "
                    + "Only orders of the caller's own shop are visible.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Order detail returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Order not found in the caller's shop, or the caller has no shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @GetMapping("/orders/{orderId}")
    public ApiResponse<SellerOrderDetailResponse> getOrderDetail(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(sellerOrderService.getOrderDetail(claims.userId(), orderId));
    }

    @Operation(
            summary = "Mark order as shipped",
            description = "Transitions a paid READY_TO_SHIP order to SHIPPED using internal/mock shipping. "
                    + "Only the owning seller can ship.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Order marked as shipped.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Order not found in the caller's shop, or the caller has no shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Order is not paid or not in READY_TO_SHIP state.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PostMapping("/orders/{orderId}/ship")
    public ApiResponse<SellerOrderDetailResponse> shipOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(sellerOrderService.shipOrder(claims.userId(), orderId));
    }

    @Operation(
            summary = "Mark order as delivered",
            description = "Transitions a SHIPPED order to DELIVERED using internal/mock shipping. "
                    + "Only the owning seller can deliver.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Order marked as delivered.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Order not found in the caller's shop, or the caller has no shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Order is not in SHIPPED state.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PostMapping("/orders/{orderId}/deliver")
    public ApiResponse<SellerOrderDetailResponse> deliverOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(sellerOrderService.deliverOrder(claims.userId(), orderId));
    }

    @Operation(
            summary = "Seller dashboard read model",
            description = "Product counts by status, order counts by fulfillment/payment state and the newest "
                    + "READY_TO_SHIP orders for the authenticated seller's shop.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Dashboard summary returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Caller does not own a shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @GetMapping("/dashboard")
    public ApiResponse<SellerDashboardResponse> getDashboard(@AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(sellerOrderService.getDashboard(claims.userId()));
    }

    private void requireAuthenticated(AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
