package com.shopee.monolith.modules.order.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.order.dto.request.CheckoutPreviewRequest;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutPreviewResponse;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;
import com.shopee.monolith.modules.order.dto.response.OrderSwaggerResponses;
import com.shopee.monolith.modules.order.service.CheckoutPreviewService;
import com.shopee.monolith.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order processing and checkout APIs")
public class OrderController {

    private final OrderService orderService;
    private final CheckoutPreviewService checkoutPreviewService;

    @Operation(
            summary = "Checkout current user's cart items",
            description = "Creates orders and reserve inventories based on current cart items.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Checkout processed successfully.",
            content = @Content(schema = @Schema(implementation = OrderSwaggerResponses.ApiResponseCheckoutResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request, missing idempotency key, or cart empty.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Idempotency key conflict, concurrent processing, or insufficient stock.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "Database or Redis service unavailable.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping
    public ApiResponse<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AccessTokenClaims claims) {

        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }

        CheckoutResponse response = orderService.checkout(claims.userId(), request, idempotencyKey);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "Preview checkout cost breakdown",
            description = "Returns per-item validation results and shipping fee breakdown for selected cart items. "
                    + "Does not reserve inventory.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Preview computed successfully.",
            content = @Content(schema = @Schema(implementation = OrderSwaggerResponses.ApiResponseCheckoutPreviewResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "No items selected, or no valid address found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "Redis service unavailable.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping("/preview")
    public ApiResponse<CheckoutPreviewResponse> previewCheckout(
            @Valid @RequestBody CheckoutPreviewRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(checkoutPreviewService.preview(claims.userId(), request));
    }
}
