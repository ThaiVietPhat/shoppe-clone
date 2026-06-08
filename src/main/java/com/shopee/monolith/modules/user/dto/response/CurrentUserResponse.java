package com.shopee.monolith.modules.user.dto.response;

import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Current authenticated user profile for client app shell")
public record CurrentUserResponse(
        @Schema(description = "User unique ID", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Registered email address", example = "seller@shoppe.local")
        String email,

        @Schema(description = "User role", example = "SELLER")
        Role role,

        @Schema(description = "Account status", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "Latest uploaded avatar media, if any")
        MediaAssetResponse avatar,

        @Schema(description = "Owned shop summary if the user has one")
        CurrentUserShopResponse shop
) {
}
