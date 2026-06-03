package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthSecurityProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            String code = UUID.randomUUID().toString();
            String key = "oauth2:code:" + code;
            String value = customUser.getUserId().toString() + ":" + customUser.getRole();

            // Redirect back to allowed origins (SPA client) with code parameter
            String allowedOrigin = properties.getCors().getAllowedOrigins().isEmpty()
                    ? "http://localhost:3000"
                    : properties.getCors().getAllowedOrigins().get(0);

            try {
                // Store exchange code in Redis for 60 seconds
                stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(60));
            } catch (Exception e) {
                String targetUrl = allowedOrigin + "/login?error=" + java.net.URLEncoder.encode("service_unavailable", java.nio.charset.StandardCharsets.UTF_8);
                getRedirectStrategy().sendRedirect(request, response, targetUrl);
                return;
            }

            String targetUrl = allowedOrigin + "/oauth2/redirect?code=" + code;
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
