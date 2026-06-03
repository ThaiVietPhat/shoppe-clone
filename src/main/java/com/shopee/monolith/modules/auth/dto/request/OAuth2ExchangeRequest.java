package com.shopee.monolith.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuth2ExchangeRequest(
        @NotBlank(message = "Code must not be blank")
        String code
) {}
