package com.shopee.monolith.modules.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(description = "Request payload for updating a product")
public record UpdateProductRequest(
        @Schema(description = "Category unique ID (optional)", example = "3b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID categoryId,

        @NotBlank(message = "Product name is required")
        @Size(min = 3, max = 255, message = "Product name must be between 3 and 255 characters")
        @Schema(description = "Name of the product", example = "iPhone 15 Pro Max",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        @Schema(description = "Product description details", example = "Updated description with battery specs")
        String description,

        @Size(max = 255, message = "Brand must not exceed 255 characters")
        @Schema(description = "Brand name", example = "Apple")
        String brand,

        @Size(max = 100, message = "Seller SKU must not exceed 100 characters")
        @Schema(description = "Seller internal SKU / reference code", example = "APPLE-IP15PROMAX-2024")
        String sellerSku,

        @Schema(description = "Flexible product attributes as key-value map",
                example = "{\"color\": \"Space Black\", \"storage\": \"512GB\"}")
        Map<String, Object> attributes,

        @Schema(description = "Replacement list of media asset IDs (replaces current attachment order)")
        List<UUID> mediaIds
) {}
