package com.shopee.monolith.modules.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "ChatMessageResponse", description = "One chat message")
public record ChatMessageResponse(
        @Schema(description = "Message ID")
        UUID id,

        @Schema(description = "Room ID")
        UUID roomId,

        @Schema(description = "Sender user ID")
        UUID senderId,

        @Schema(description = "Message text", example = "Is this item still in stock?")
        String content,

        @Schema(description = "Timestamp when sent")
        Instant createdAt
) {
}
