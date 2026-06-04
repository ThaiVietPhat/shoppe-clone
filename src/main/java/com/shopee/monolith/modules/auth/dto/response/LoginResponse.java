package com.shopee.monolith.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Response payload containing the access token")
public record LoginResponse(
        @Schema(description = "JWT Access Token used for authenticated routes", example = "<jwt-access-token>")
        String accessToken
) {}
