package com.shopee.monolith.modules.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.security")
@Validated
@Getter
@Setter
public class AuthSecurityProperties {

    @Valid
    @NotNull
    private AuthCookieProperties authCookie = new AuthCookieProperties();

    @Valid
    @NotNull
    private CorsProperties cors = new CorsProperties();

    @Valid
    @NotNull
    private CsrfProperties csrf = new CsrfProperties();

    private List<String> trustedProxies = List.of();

    @Getter
    @Setter
    public static class AuthCookieProperties {
        @NotBlank
        private String name = "__Secure-refresh_token";
        private boolean secure = true;
        private boolean httpOnly = true;
        @NotBlank
        private String sameSite = "Lax";
        @NotBlank
        private String path = "/api/auth";
    }

    @Getter
    @Setter
    public static class CorsProperties {
        @NotEmpty
        private List<String> allowedOrigins = List.of("http://localhost:3000");
        private boolean allowCredentials = true;
    }

    @Getter
    @Setter
    public static class CsrfProperties {
        @NotBlank
        private String cookieName = "XSRF-TOKEN";
        @NotBlank
        private String headerName = "X-XSRF-TOKEN";
    }
}
