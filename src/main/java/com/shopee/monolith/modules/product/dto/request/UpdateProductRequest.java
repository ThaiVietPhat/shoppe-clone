package com.shopee.monolith.modules.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Request payload for updating a product")
public record UpdateProductRequest(
        @Schema(description = "Category unique ID (optional)", example = "3b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID categoryId,

        @NotBlank(message = "Product name is required")
        @Size(min = 3, max = 255, message = "Product name must be between 3 and 255 characters")
        @Schema(description = "Name of the product", example = "iPhone 15 Pro Max", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        @Schema(description = "Product description details", example = "Updated description with battery specs")
        String description
) {}
