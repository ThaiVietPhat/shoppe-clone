package com.shopee.monolith.modules.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(description = "Request payload for creating a product")
public record CreateProductRequest(
        @NotNull(message = "Shop ID is required")
        @Schema(description = "Shop unique ID", example = "4a123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID shopId,

        @Schema(description = "Category unique ID (optional)", example = "3b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID categoryId,

        @NotBlank(message = "Product name is required")
        @Size(min = 3, max = 255, message = "Product name must be between 3 and 255 characters")
        @Schema(description = "Name of the product", example = "iPhone 15 Pro",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        @Schema(description = "Product description", example = "The latest iPhone with titanium design")
        String description,

        @Size(max = 255, message = "Brand must not exceed 255 characters")
        @Schema(description = "Brand name", example = "Apple")
        String brand,

        @Size(max = 100, message = "Seller SKU must not exceed 100 characters")
        @Schema(description = "Seller internal SKU / reference code", example = "APPLE-IP15PRO-2024")
        String sellerSku,

        @Schema(description = "Flexible product attributes as key-value map",
                example = "{\"color\": \"Space Black\", \"storage\": \"256GB\"}")
        Map<String, Object> attributes,

        @Schema(description = "List of media asset IDs to attach to this product on creation")
        List<UUID> mediaIds
) {}
