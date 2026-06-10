package com.shopee.monolith.modules.cart.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.cart.dto.request.AddCartItemRequest;
import com.shopee.monolith.modules.cart.dto.request.CartSelectRequest;
import com.shopee.monolith.modules.cart.dto.request.UpdateCartItemRequest;
import com.shopee.monolith.modules.cart.dto.response.CartResponse;
import com.shopee.monolith.modules.cart.dto.response.CartSwaggerResponses;
import com.shopee.monolith.modules.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management APIs")
public class CartController {

    private final CartService cartService;

    @Operation(
            summary = "Get current user's shopping cart",
            description = "Retrieves all items inside the shopping cart for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Cart retrieved successfully.",
            content = @Content(schema = @Schema(implementation = CartSwaggerResponses.ApiResponseCartResponse.class))
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
    @GetMapping
    public ApiResponse<CartResponse> getCart(@AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(cartService.getCart(claims.userId()));
    }

    @Operation(
            summary = "Add an item to the shopping cart",
            description = "Adds a variant with specified quantity to the shopping cart.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Item added to cart successfully.",
            content = @Content(schema = @Schema(implementation = CartSwaggerResponses.ApiResponseCartResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request or quantity bounds exceeded.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product variant not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "Redis service unavailable.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(cartService.addItem(claims.userId(), request));
    }

    @Operation(
            summary = "Update item quantity in cart",
            description = "Updates the quantity of an item in the cart. Set quantity to 0 to remove.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Item quantity updated successfully.",
            content = @Content(schema = @Schema(implementation = CartSwaggerResponses.ApiResponseCartResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request or quantity bounds exceeded.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product variant not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "Redis service unavailable.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PatchMapping("/items/{variantId}")
    public ApiResponse<CartResponse> updateItem(
            @Parameter(description = "Product variant unique ID") @PathVariable UUID variantId,
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(cartService.updateItem(claims.userId(), variantId, request));
    }

    @Operation(
            summary = "Remove an item from the shopping cart",
            description = "Removes the specified product variant from the authenticated user's cart.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Item removed successfully.",
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
    @DeleteMapping("/items/{variantId}")
    public ApiResponse<Void> removeItem(
            @Parameter(description = "Product variant unique ID") @PathVariable UUID variantId,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        cartService.removeItem(claims.userId(), variantId);
        return ApiResponse.success();
    }

    @Operation(
            summary = "Clear the shopping cart",
            description = "Clears all items inside the authenticated user's shopping cart.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Cart cleared successfully.",
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
    @DeleteMapping
    public ApiResponse<Void> clearCart(@AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        cartService.clearCart(claims.userId());
        return ApiResponse.success();
    }

    @Operation(
            summary = "Select items for checkout",
            description = "Marks the given variant IDs as selected. Only variants already in the cart can be selected.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Items selected successfully.",
            content = @Content(schema = @Schema(implementation = CartSwaggerResponses.ApiResponseCartResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "One or more variant IDs are not in the cart.",
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
    @PostMapping("/items/select")
    public ApiResponse<CartResponse> selectItems(
            @Valid @RequestBody CartSelectRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(cartService.selectItems(claims.userId(), request.variantIds()));
    }

    @Operation(
            summary = "Deselect items from checkout",
            description = "Removes the given variant IDs from the selected set.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Items deselected successfully.",
            content = @Content(schema = @Schema(implementation = CartSwaggerResponses.ApiResponseCartResponse.class))
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
    @PostMapping("/items/deselect")
    public ApiResponse<CartResponse> deselectItems(
            @Valid @RequestBody CartSelectRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(cartService.deselectItems(claims.userId(), request.variantIds()));
    }
}
