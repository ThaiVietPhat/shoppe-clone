package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public final class ProductSwaggerResponses {

    private ProductSwaggerResponses() {}

    @Schema(name = "ApiResponseCategoryResponse", description = "API response wrapper containing CategoryResponse")
    public record ApiResponseCategoryResponse(
            int code,
            String message,
            CategoryResponse data
    ) {}

    @Schema(name = "ApiResponseCategoryList", description = "API response wrapper containing List of CategoryResponse")
    public record ApiResponseCategoryList(
            int code,
            String message,
            List<CategoryResponse> data
    ) {}

    @Schema(name = "ApiResponseProductResponse", description = "API response wrapper containing ProductResponse")
    public record ApiResponseProductResponse(
            int code,
            String message,
            ProductResponse data
    ) {}

    @Schema(name = "ApiResponseProductList", description = "API response wrapper containing List of ProductResponse")
    public record ApiResponseProductList(
            int code,
            String message,
            List<ProductResponse> data
    ) {}

    @Schema(name = "ApiResponseProductVariantResponse", description = "API response wrapper containing ProductVariantResponse")
    public record ApiResponseProductVariantResponse(
            int code,
            String message,
            ProductVariantResponse data
    ) {}

    @Schema(name = "ApiResponseProductVariantList", description = "API response wrapper containing List of ProductVariantResponse")
    public record ApiResponseProductVariantList(
            int code,
            String message,
            List<ProductVariantResponse> data
    ) {}
}
