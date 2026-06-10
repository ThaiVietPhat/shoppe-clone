package com.shopee.monolith.modules.payment.dto.request;

import com.shopee.monolith.modules.payment.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(name = "InitiatePaymentRequest", description = "Payload for initiating a payment attempt on a checkout session")
public record InitiatePaymentRequest(
        @Schema(description = "Checkout session to pay for",
                example = "8f14e45f-ceea-467f-9c4e-1d2a3b4c5d6e",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID checkoutSessionId,

        @Schema(description = "Payment method", example = "COD", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PaymentMethod method
) {
}
