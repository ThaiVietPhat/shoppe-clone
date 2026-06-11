package com.shopee.monolith.modules.inventory.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.inventory.dto.request.CreateInventoryRequest;
import com.shopee.monolith.modules.inventory.dto.request.UpdateStockRequest;
import com.shopee.monolith.modules.inventory.dto.response.InventoryMovementResponse;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;
import com.shopee.monolith.modules.inventory.dto.response.InventorySwaggerResponses;
import com.shopee.monolith.modules.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Inventory", description = "Seller and admin inventory management APIs")
public class InventoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryService inventoryService;

    @Operation(
            summary = "Create inventory",
            description = "Creates inventory for a specific product variant.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory created successfully.",
            content = @Content(schema = @Schema(implementation = InventorySwaggerResponses.ApiResponseInventoryResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request details.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner or admin permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Inventory already exists for this variant.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping("/api/inventories")
    public ApiResponse<InventoryResponse> createInventory(
            @Valid @RequestBody CreateInventoryRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(inventoryService.createInventory(request.variantId(), request.initialStock(), claims.userId(), claims.role()));
    }

    @Operation(
            summary = "Get inventory by variant ID",
            description = "Retrieves inventory details for a specific product variant.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory details retrieved successfully.",
            content = @Content(schema = @Schema(implementation = InventorySwaggerResponses.ApiResponseInventoryResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner or admin permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Inventory not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @GetMapping("/api/inventories/variants/{variantId}")
    public ApiResponse<InventoryResponse> getInventoryByVariantId(
            @Parameter(description = "Product variant unique ID") @PathVariable UUID variantId,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(inventoryService.getInventoryByVariantId(variantId, claims.userId(), claims.role()));
    }

    @Operation(
            summary = "Update available stock",
            description = "Directly sets available stock for a specific product variant.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Available stock updated successfully.",
            content = @Content(schema = @Schema(implementation = InventorySwaggerResponses.ApiResponseInventoryResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid available stock quantity.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner or admin permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Inventory not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PatchMapping("/api/inventories/variants/{variantId}/stock")
    public ApiResponse<InventoryResponse> updateAvailableStock(
            @Parameter(description = "Product variant unique ID") @PathVariable UUID variantId,
            @Valid @RequestBody UpdateStockRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(inventoryService.updateAvailableStock(variantId, request.availableStock(), claims.userId(), claims.role()));
    }

    @Operation(
            summary = "List inventory movement ledger",
            description = "Paged audit ledger of stock movements for one variant, newest first. "
                    + "Only the owning seller or an admin can read it.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Movement ledger returned."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner or admin permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Variant not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @GetMapping("/api/inventories/variants/{variantId}/movements")
    public ApiResponse<PagedResponse<InventoryMovementResponse>> listMovements(
            @Parameter(description = "Product variant unique ID") @PathVariable UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.success(inventoryService.listMovements(
                variantId, claims.userId(), claims.role(), PageRequest.of(Math.max(page, 0), cappedSize)));
    }
}
