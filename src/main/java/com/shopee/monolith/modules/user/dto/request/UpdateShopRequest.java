package com.shopee.monolith.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for updating seller shop profile details")
public record UpdateShopRequest(
        @NotBlank(message = "Shop name is required")
        @Size(min = 3, max = 100, message = "Shop name must be between 3 and 100 characters")
        @Schema(description = "Name of the shop", example = "Shopee Mall Demo Updated", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        @Schema(description = "Shop description", example = "Updated description for official store")
        String description
) {}
