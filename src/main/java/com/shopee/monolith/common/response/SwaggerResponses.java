package com.shopee.monolith.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Common response schemas for Swagger/OpenAPI documentation.
 * This class resides in common and must remain independent of any business modules.
 */
public final class SwaggerResponses {

    private SwaggerResponses() {}

    @Schema(name = "ApiResponseVoid", description = "API response wrapper with no data payload")
    public record ApiResponseVoid(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message
    ) {}
}
