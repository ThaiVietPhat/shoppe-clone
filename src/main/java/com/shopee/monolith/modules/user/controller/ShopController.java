package com.shopee.monolith.modules.user.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.user.dto.request.CreateShopRequest;
import com.shopee.monolith.modules.user.dto.request.UpdateShopRequest;
import com.shopee.monolith.modules.user.dto.response.ShopResponse;
import com.shopee.monolith.modules.user.dto.response.ShopSwaggerResponses;
import com.shopee.monolith.modules.user.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@Tag(name = "Shops", description = "Shop registration and seller shop profile APIs")
public class ShopController {

    private final ShopService shopService;

    @Operation(
            summary = "Create current user's shop",
            description = "Creates one seller shop for the authenticated active user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Shop created successfully.",
            content = @Content(schema = @Schema(implementation = ShopSwaggerResponses.ApiResponseShopResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Account is not active.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "User already owns a shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping
    public ApiResponse<ShopResponse> createShop(
            @Valid @RequestBody CreateShopRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(shopService.createShop(claims.userId(), request));
    }

    @Operation(
            summary = "Get current user's shop profile",
            description = "Retrieves shop profile details for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Shop profile retrieved successfully.",
            content = @Content(schema = @Schema(implementation = ShopSwaggerResponses.ApiResponseShopResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Shop not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @GetMapping("/me")
    public ApiResponse<ShopResponse> getMyShop(@AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(shopService.getShopByOwnerId(claims.userId()));
    }

    @Operation(
            summary = "Update current user's shop profile",
            description = "Updates the shop profile details for the authenticated seller.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Shop profile updated successfully.",
            content = @Content(schema = @Schema(implementation = ShopSwaggerResponses.ApiResponseShopResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Shop not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PatchMapping("/me")
    public ApiResponse<ShopResponse> updateMyShop(
            @Valid @RequestBody UpdateShopRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(shopService.updateShop(claims.userId(), request));
    }

    @Operation(
            summary = "Lookup shop by ID",
            description = "Retrieves public details of a shop by its unique ID."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Shop lookup successful.",
            content = @Content(schema = @Schema(implementation = ShopSwaggerResponses.ApiResponseShopResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Shop not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @GetMapping("/{shopId}")
    public ApiResponse<ShopResponse> getShopById(
            @Parameter(description = "Shop unique ID") @PathVariable UUID shopId) {
        return ApiResponse.success(shopService.getShopById(shopId));
    }
}
