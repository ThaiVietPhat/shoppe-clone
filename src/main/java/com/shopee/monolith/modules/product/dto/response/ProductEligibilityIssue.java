package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Seller-visible product publish or checkout eligibility issue")
public enum ProductEligibilityIssue {
    PRODUCT_NOT_ACTIVE,
    NO_ACTIVE_VARIANT,
    NO_POSITIVE_PRICE,
    NO_STOCK
}
