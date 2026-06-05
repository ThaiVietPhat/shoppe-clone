package com.shopee.monolith.modules.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Payload to initialize stock for a product variant")
public record CreateInventoryRequest(
        @Schema(description = "UUID of the product variant", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Variant ID is required")
        UUID variantId,

        @Schema(description = "Initial available stock quantity", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 0, message = "Initial stock must be non-negative")
        int initialStock
) {}
