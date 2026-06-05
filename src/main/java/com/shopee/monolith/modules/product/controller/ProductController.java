package com.shopee.monolith.modules.product.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.product.dto.request.CreateProductRequest;
import com.shopee.monolith.modules.product.dto.request.CreateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.response.CategoryResponse;
import com.shopee.monolith.modules.product.dto.response.ProductResponse;
import com.shopee.monolith.modules.product.dto.response.ProductSwaggerResponses;
import com.shopee.monolith.modules.product.dto.response.ProductVariantResponse;
import com.shopee.monolith.modules.product.service.ProductService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product categories, catalog, and seller product variant management APIs")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "List all categories", description = "Retrieves a list of all active categories.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Categories list retrieved successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseCategoryList.class))
    )
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.success(productService.listCategories());
    }

    @Operation(summary = "List all products", description = "Retrieves all products in the e-commerce catalog.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Products list retrieved successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductList.class))
    )
    @GetMapping("/api/products")
    public ApiResponse<List<ProductResponse>> listProducts() {
        return ApiResponse.success(productService.listProducts());
    }

    @Operation(summary = "Get product by ID", description = "Retrieves detailed product profile and variants by product ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product details retrieved successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @GetMapping("/api/products/{productId}")
    public ApiResponse<ProductResponse> getProductById(
            @Parameter(description = "Product unique ID") @PathVariable UUID productId) {
        return ApiResponse.success(productService.getProductById(productId));
    }

    @Operation(summary = "List shop products", description = "Retrieves all catalog products owned by a specific seller shop.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Shop products list retrieved successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductList.class))
    )
    @GetMapping("/api/shops/{shopId}/products")
    public ApiResponse<List<ProductResponse>> listProductsByShop(
            @Parameter(description = "Shop unique ID") @PathVariable UUID shopId) {
        return ApiResponse.success(productService.listProductsByShop(shopId));
    }

    @Operation(
            summary = "Create product",
            description = "Allows a seller to register a new product under their shop.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product created successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Shop or Category not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping("/api/products")
    public ApiResponse<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(productService.createProduct(claims.userId(), request));
    }

    @Operation(
            summary = "Update product details",
            description = "Allows a seller to modify basic description or category details of their product.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product updated successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product or Category not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PatchMapping("/api/products/{productId}")
    public ApiResponse<ProductResponse> updateProduct(
            @Parameter(description = "Product unique ID") @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(productService.updateProduct(claims.userId(), productId, request));
    }

    @Operation(
            summary = "Create product variant",
            description = "Adds a new unique SKU variant to an existing product.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product variant created successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductVariantResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request details or price format.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "SKU already exists.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PostMapping("/api/products/{productId}/variants")
    public ApiResponse<ProductVariantResponse> createVariant(
            @Parameter(description = "Product unique ID") @PathVariable UUID productId,
            @Valid @RequestBody CreateProductVariantRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(productService.createVariant(claims.userId(), productId, request));
    }

    @Operation(
            summary = "Update product variant details",
            description = "Updates pricing or details of an existing SKU variant.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product variant updated successfully.",
            content = @Content(schema = @Schema(implementation = ProductSwaggerResponses.ApiResponseProductVariantResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request details or price format.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Shop owner permission required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product or Variant not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "SKU already exists.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @PatchMapping("/api/products/{productId}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> updateVariant(
            @Parameter(description = "Product unique ID") @PathVariable UUID productId,
            @Parameter(description = "Product variant unique ID") @PathVariable UUID variantId,
            @Valid @RequestBody UpdateProductVariantRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(productService.updateVariant(claims.userId(), productId, variantId, request));
    }
}
