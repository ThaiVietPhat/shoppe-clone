package com.shopee.monolith.modules.user.dto.response;

import com.shopee.monolith.modules.user.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Response payload containing registered user details")
public record UserResponse(
        @Schema(description = "User unique ID", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,
        @Schema(description = "Registered email address", example = "buyer@shoppe.local")
        String email,
        @Schema(description = "User account role", example = "BUYER")
        Role role
) {
}
