package com.shopee.monolith.modules.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(name = "SendChatMessageRequest", description = "Payload for sending one chat message")
public record SendChatMessageRequest(
        @Schema(description = "Message text", example = "Is this item still in stock?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 2000)
        String content
) {
}
