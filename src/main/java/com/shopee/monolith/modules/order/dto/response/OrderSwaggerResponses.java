package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public final class OrderSwaggerResponses {

    private OrderSwaggerResponses() {}

    @Schema(name = "ApiResponseCheckoutResponse", description = "API response wrapper containing checkout details")
    public record ApiResponseCheckoutResponse(
            @Schema(description = "HTTP code", example = "200")
            int code,

            @Schema(description = "Message description", example = "Success")
            String message,

            CheckoutResponse data
    ) {}

    @Schema(name = "ApiResponseCheckoutPreviewResponse", description = "API response wrapper containing checkout preview")
    public record ApiResponseCheckoutPreviewResponse(
            @Schema(description = "HTTP code", example = "200")
            int code,

            @Schema(description = "Message description", example = "Success")
            String message,

            CheckoutPreviewResponse data
    ) {}
}
