package com.shopee.monolith.modules.notification.dto.response;

import com.shopee.monolith.modules.notification.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "NotificationResponse", description = "One inbox notification of the authenticated user")
public record NotificationResponse(
        @Schema(description = "Notification ID")
        UUID id,

        @Schema(description = "Notification type", example = "ORDER_CONFIRMED")
        NotificationType type,

        @Schema(description = "Short title", example = "Your order has been confirmed")
        String title,

        @Schema(description = "Optional body text", example = "Order paid via COD and is being prepared.")
        String body,

        @Schema(description = "Type of the referenced resource", example = "ORDER")
        String refType,

        @Schema(description = "ID of the referenced resource")
        UUID refId,

        @Schema(description = "Read timestamp (null when unread)")
        Instant readAt,

        @Schema(description = "Timestamp when created")
        Instant createdAt
) {
}
