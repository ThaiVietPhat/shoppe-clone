package com.shopee.monolith.modules.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(name = "CheckoutRequest", description = "Payload for order checkout creation request")
public record CheckoutRequest(
        @Schema(
                description = "Street address for shipping",
                example = "123 Main Street",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Shipping street is required")
        String shippingStreet,

        @Schema(
                description = "City for shipping",
                example = "Hanoi",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Shipping city is required")
        String shippingCity
) {}
