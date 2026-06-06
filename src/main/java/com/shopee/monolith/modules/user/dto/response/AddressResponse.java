package com.shopee.monolith.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Response payload containing address details")
public record AddressResponse(
        @Schema(description = "Address unique ID", example = "7a123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "User unique ID owning the address", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID userId,

        @Schema(description = "Recipient name", example = "John Doe")
        String recipientName,

        @Schema(description = "Recipient phone number", example = "0987654321")
        String phone,

        @Schema(description = "Street address line", example = "123 Main Street")
        String addressLine,

        @Schema(description = "GHN ward code", example = "20314")
        String wardCode,

        @Schema(description = "Ward name", example = "Phường Liễu Giai")
        String wardName,

        @Schema(description = "GHN district code", example = "1442")
        String districtCode,

        @Schema(description = "District name", example = "Quận Ba Đình")
        String districtName,

        @Schema(description = "GHN province code", example = "201")
        String provinceCode,

        @Schema(description = "Province/City name", example = "Thành phố Hà Nội")
        String provinceName,

        @Schema(description = "Indicates if this is the default address", example = "true")
        boolean isDefault,

        @Schema(description = "Timestamp when the address was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the address was last updated")
        Instant updatedAt
) {}
