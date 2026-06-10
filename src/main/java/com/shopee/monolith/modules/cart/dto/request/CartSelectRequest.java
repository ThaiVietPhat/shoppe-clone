package com.shopee.monolith.modules.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
@Schema(name = "CartSelectRequest", description = "List of variant IDs to select or deselect for checkout")
public record CartSelectRequest(
        @NotEmpty
        @Schema(description = "Variant IDs to select or deselect", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UUID> variantIds
) {}
