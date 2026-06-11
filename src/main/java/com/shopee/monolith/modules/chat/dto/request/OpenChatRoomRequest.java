package com.shopee.monolith.modules.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(name = "OpenChatRoomRequest", description = "Payload for opening (or reusing) a buyer-shop chat room")
public record OpenChatRoomRequest(
        @Schema(description = "Shop to chat with", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID shopId
) {
}
