package com.shopee.monolith.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for creating or updating an address")
public record AddressRequest(
        @NotBlank(message = "Recipient name is required")
        @Schema(description = "Name of the recipient", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
        String recipientName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$", message = "Invalid Vietnamese phone number")
        @Schema(description = "Phone number of the recipient", example = "0987654321", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,

        @NotBlank(message = "Address line is required")
        @Schema(description = "Street address line", example = "123 Main Street", requiredMode = Schema.RequiredMode.REQUIRED)
        String addressLine,

        @NotBlank(message = "Ward code is required")
        @Schema(description = "GHN compatible ward code", example = "20314", requiredMode = Schema.RequiredMode.REQUIRED)
        String wardCode,

        @NotBlank(message = "Ward name is required")
        @Schema(description = "Ward name", example = "Phường Liễu Giai", requiredMode = Schema.RequiredMode.REQUIRED)
        String wardName,

        @NotBlank(message = "District code is required")
        @Schema(description = "GHN compatible district code", example = "1442", requiredMode = Schema.RequiredMode.REQUIRED)
        String districtCode,

        @NotBlank(message = "District name is required")
        @Schema(description = "District name", example = "Quận Ba Đình", requiredMode = Schema.RequiredMode.REQUIRED)
        String districtName,

        @NotBlank(message = "Province code is required")
        @Schema(description = "GHN compatible province code", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
        String provinceCode,

        @NotBlank(message = "Province name is required")
        @Schema(description = "Province/City name", example = "Thành phố Hà Nội", requiredMode = Schema.RequiredMode.REQUIRED)
        String provinceName,

        @NotNull(message = "isDefault flag is required")
        @Schema(description = "Mark this address as default", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean isDefault
) {}
