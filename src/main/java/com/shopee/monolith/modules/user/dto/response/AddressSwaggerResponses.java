package com.shopee.monolith.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class AddressSwaggerResponses {

    private AddressSwaggerResponses() {}

    @Schema(name = "ApiResponseAddressResponse", description = "API response wrapper containing AddressResponse")
    public record ApiResponseAddressResponse(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message,

            AddressResponse data
    ) {}

    @Schema(name = "ApiResponseAddressListResponse", description = "API response wrapper containing a list of AddressResponses")
    public record ApiResponseAddressListResponse(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message,

            List<AddressResponse> data
    ) {}
}
