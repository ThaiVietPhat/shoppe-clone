package com.shopee.monolith.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank(message = "Shipping street is required")
        String shippingStreet,

        @NotBlank(message = "Shipping city is required")
        String shippingCity
) {}
