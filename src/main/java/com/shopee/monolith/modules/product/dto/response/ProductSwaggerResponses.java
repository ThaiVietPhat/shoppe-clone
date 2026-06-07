package com.shopee.monolith.modules.product.dto.response;

import com.shopee.monolith.common.response.PagedResponse;
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

    @Schema(name = "ApiResponsePagedProductResponse", description = "API response wrapper containing PagedResponse of ProductResponse")
    public record ApiResponsePagedProductResponse(
            int code,
            String message,
            PagedResponse<ProductResponse> data
    ) {}

    @Schema(name = "ApiResponseProductDetailResponse", description = "API response wrapper containing ProductDetailResponse")
    public record ApiResponseProductDetailResponse(
            int code,
            String message,
            ProductDetailResponse data
    ) {}

    @Schema(name = "ApiResponsePagedProductCardResponse", description = "API response wrapper containing PagedResponse of ProductCardResponse")
    public record ApiResponsePagedProductCardResponse(
            int code,
            String message,
            PagedResponse<ProductCardResponse> data
    ) {}

    @Schema(name = "ApiResponsePagedProductDetailResponse", description = "API response wrapper containing PagedResponse of ProductDetailResponse")
    public record ApiResponsePagedProductDetailResponse(
            int code,
            String message,
            PagedResponse<ProductDetailResponse> data
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
