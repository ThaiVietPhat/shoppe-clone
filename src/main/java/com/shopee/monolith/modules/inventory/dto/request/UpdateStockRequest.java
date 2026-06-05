package com.shopee.monolith.modules.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Builder;

@Builder
@Schema(description = "Payload to update available stock of an inventory")
public record UpdateStockRequest(
        @Schema(description = "New available stock quantity", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 0, message = "Available stock must be non-negative")
        int availableStock
) {}
