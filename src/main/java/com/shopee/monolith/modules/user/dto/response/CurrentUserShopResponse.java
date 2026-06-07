package com.shopee.monolith.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Current user's owned shop summary")
public record CurrentUserShopResponse(
        @Schema(description = "Shop unique ID", example = "4a123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Shop display name", example = "Shopee Mall Demo")
        String name
) {
}
