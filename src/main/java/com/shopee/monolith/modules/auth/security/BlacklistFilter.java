package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.service.AccessTokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class BlacklistFilter extends OncePerRequestFilter {

    private final AccessTokenBlacklistService blacklistService;
    private final SecurityErrorWriter securityErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if (path != null && path.equals("/api/auth/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccessTokenClaims claims) {
            try {
                if (blacklistService.isBlacklisted(claims.jti())) {
                    securityErrorWriter.writeError(response, ErrorCode.INVALID_TOKEN);
                    return;
                }
            } catch (AppException e) {
                securityErrorWriter.writeError(response, e.getErrorCode());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
