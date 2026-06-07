package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Response payload containing product category details")
public record CategoryResponse(
        @Schema(description = "Category unique ID", example = "3b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Parent category unique ID (optional)", example = "2a123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID parentId,

        @Schema(description = "Category name", example = "Electronics")
        String name,

        @Schema(description = "Materialized category path", example = "Electronics/Mobile Phones")
        String path,

        @Schema(description = "Timestamp when the category was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the category was last updated")
        Instant updatedAt
) {}
