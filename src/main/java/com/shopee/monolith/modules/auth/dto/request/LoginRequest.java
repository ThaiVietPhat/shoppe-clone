package com.shopee.monolith.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for user login")
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Schema(description = "User email address", example = "buyer@shoppe.local")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(description = "User password", example = "<password>")
        String password
) {}
