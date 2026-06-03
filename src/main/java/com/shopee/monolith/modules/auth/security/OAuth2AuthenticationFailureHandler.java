package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AuthSecurityProperties properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String allowedOrigin = properties.getCors().getAllowedOrigins().isEmpty()
                ? "http://localhost:3000"
                : properties.getCors().getAllowedOrigins().get(0);

        String errorCode = "oauth_failed";
        if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth2Exception) {
            String oauth2ErrorCode = oauth2Exception.getError().getErrorCode();
            errorCode = switch (oauth2ErrorCode) {
                case "email_already_exists" -> "email_already_exists";
                case "email_not_verified" -> "email_not_verified";
                case "account_locked", "account_inactive" -> "account_locked";
                case "service_unavailable" -> "service_unavailable";
                default -> "oauth_failed";
            };
        } else {
            String message = exception.getMessage();
            if (message != null) {
                if (message.contains("locked")) {
                    errorCode = "account_locked";
                } else if (message.contains("inactive")) {
                    errorCode = "account_locked";
                } else if (message.contains("already exists")) {
                    errorCode = "email_already_exists";
                } else if (message.contains("not verified")) {
                    errorCode = "email_not_verified";
                } else if (message.contains("unavailable") || message.contains("Redis")) {
                    errorCode = "service_unavailable";
                }
            }
        }

        String targetUrl = allowedOrigin + "/login?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
