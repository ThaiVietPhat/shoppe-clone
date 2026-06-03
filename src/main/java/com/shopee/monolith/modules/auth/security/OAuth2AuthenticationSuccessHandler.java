package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import com.shopee.monolith.modules.user.model.Role;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RefreshTokenService refreshTokenService;
    private final AuthSecurityProperties properties;
    private final JwtProperties jwtProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            IssuedTokenPair tokenPair = refreshTokenService.issueTokenPair(
                    customUser.getUserId(),
                    Role.valueOf(customUser.getRole())
            );

            // Set cookie refresh token
            var cookieProperties = properties.getAuthCookie();
            ResponseCookie cookie = ResponseCookie.from(cookieProperties.getName(), tokenPair.refreshToken())
                    .httpOnly(cookieProperties.isHttpOnly())
                    .secure(cookieProperties.isSecure())
                    .path(cookieProperties.getPath())
                    .maxAge(jwtProperties.getRefreshExpiration())
                    .sameSite(cookieProperties.getSameSite())
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            // Redirect back to allowed origins (SPA client)
            String allowedOrigin = properties.getCors().getAllowedOrigins().isEmpty()
                    ? "http://localhost:3000"
                    : properties.getCors().getAllowedOrigins().get(0);
            String targetUrl = allowedOrigin + "/oauth2/redirect?token=" + tokenPair.accessToken();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
