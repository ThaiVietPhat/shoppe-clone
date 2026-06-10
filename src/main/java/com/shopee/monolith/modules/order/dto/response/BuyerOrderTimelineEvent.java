package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(name = "BuyerOrderTimelineEvent", description = "Order timeline event derived from order and payment state")
public record BuyerOrderTimelineEvent(
        @Schema(description = "Timeline event type", example = "PLACED")
        String event,

        @Schema(description = "Timestamp of the event")
        Instant occurredAt
) {
}
