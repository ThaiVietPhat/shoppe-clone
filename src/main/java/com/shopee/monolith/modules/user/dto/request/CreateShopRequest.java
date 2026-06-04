package com.shopee.monolith.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for creating a seller shop")
public record CreateShopRequest(
        @NotBlank(message = "Shop name is required")
        @Size(min = 3, max = 100, message = "Shop name must be between 3 and 100 characters")
        @Schema(description = "Name of the shop", example = "Shopee Mall Demo")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        @Schema(description = "Optional shop description", example = "Official store for demo products")
        String description
) {}
