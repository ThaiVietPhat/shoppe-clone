package com.shopee.monolith.modules.auth.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.request.LoginRequest;
import com.shopee.monolith.modules.auth.dto.response.LoginResponse;
import com.shopee.monolith.modules.auth.service.AuthService;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import com.shopee.monolith.modules.auth.service.SessionRevocationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final SessionRevocationService revocationService;
    private final AuthSecurityProperties properties;
    private final JwtProperties jwtProperties;

    @GetMapping("/csrf")
    public ApiResponse<Void> getCsrfToken(HttpServletRequest request) {
        org.springframework.security.web.csrf.CsrfToken token =
                (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        IssuedTokenPair tokenPair = authService.login(request);
        setRefreshTokenCookie(response, tokenPair.refreshToken(), jwtProperties.getRefreshExpiration());
        return ApiResponse.success(new LoginResponse(tokenPair.accessToken()));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = getRefreshTokenFromCookiesOrNull(request);
        if (rawToken == null) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        IssuedTokenPair tokenPair = refreshTokenService.rotate(rawToken);
        setRefreshTokenCookie(response, tokenPair.refreshToken(), jwtProperties.getRefreshExpiration());
        return ApiResponse.success(new LoginResponse(tokenPair.accessToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = getRefreshTokenFromCookiesOrNull(request);
        if (rawToken != null) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AccessTokenClaims claims) {
                revocationService.logout(rawToken, claims);
            } else {
                revocationService.logout(rawToken, null);
            }
        }
        clearRefreshTokenCookie(response);
        return ApiResponse.success();
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccessTokenClaims claims) {
            revocationService.logoutAll(claims.userId());
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        clearRefreshTokenCookie(response);
        return ApiResponse.success();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, Duration duration) {
        var cookieProperties = properties.getAuthCookie();
        ResponseCookie cookie = ResponseCookie.from(cookieProperties.getName(), token)
                .httpOnly(cookieProperties.isHttpOnly())
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(duration)
                .sameSite(cookieProperties.getSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        var cookieProperties = properties.getAuthCookie();
        ResponseCookie cookie = ResponseCookie.from(cookieProperties.getName(), "")
                .httpOnly(cookieProperties.isHttpOnly())
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(0)
                .sameSite(cookieProperties.getSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String getRefreshTokenFromCookiesOrNull(HttpServletRequest request) {
        var cookieProperties = properties.getAuthCookie();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieProperties.getName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
