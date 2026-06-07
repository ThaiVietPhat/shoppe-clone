package com.shopee.monolith.modules.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
@Schema(description = "Request payload for updating a product variant")
public record UpdateProductVariantRequest(
        @NotBlank(message = "SKU is required")
        @Size(min = 3, max = 100, message = "SKU must be between 3 and 100 characters")
        @Schema(description = "Stock keeping unit unique identifier", example = "IPHONE15-PRO-512-GLD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String sku,

        @NotBlank(message = "Variant name is required")
        @Size(min = 1, max = 255, message = "Variant name must be between 1 and 255 characters")
        @Schema(description = "Name of the variant", example = "512GB Gold Titanium",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Schema(description = "Price of this variant", example = "1299.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal price,

        @Schema(description = "Option labels for display",
                example = "{\"color\": \"Gold Titanium\", \"storage\": \"512GB\"}")
        Map<String, String> optionLabels,

        @Schema(description = "Whether this variant is active and eligible for checkout",
                example = "true")
        Boolean active
) {}
