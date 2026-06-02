package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.service.AccessTokenBlacklistService;
import com.shopee.monolith.modules.user.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlacklistFilterTest {

    @Mock
    private AccessTokenBlacklistService blacklistService;

    @Mock
    private SecurityErrorWriter securityErrorWriter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private BlacklistFilter blacklistFilter;

    @BeforeEach
    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternalWhenAnonymousShouldSkipAndContinueChain() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(null);

        blacklistFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(blacklistService, never()).isBlacklisted(null);
    }

    @Test
    void doFilterInternalWhenPrincipalNotAccessTokenClaimsShouldSkipAndContinueChain() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        blacklistFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(blacklistService, never()).isBlacklisted(null);
    }

    @Test
    void doFilterInternalWhenNotBlacklistedShouldContinueChain() throws ServletException, IOException {
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(UUID.randomUUID())
                .role(Role.BUYER)
                .jti("jti-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(blacklistService.isBlacklisted("jti-123")).thenReturn(false);

        blacklistFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalWhenBlacklistedShouldWriteErrorAndAbort() throws ServletException, IOException {
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(UUID.randomUUID())
                .role(Role.BUYER)
                .jti("jti-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(blacklistService.isBlacklisted("jti-123")).thenReturn(true);

        blacklistFilter.doFilterInternal(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.INVALID_TOKEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternalWhenRedisFailsShouldFailClosedWriteServiceUnavailableAndAbort() throws ServletException, IOException {
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(UUID.randomUUID())
                .role(Role.BUYER)
                .jti("jti-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(blacklistService.isBlacklisted("jti-123")).thenThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE));

        blacklistFilter.doFilterInternal(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.SERVICE_UNAVAILABLE);
        verify(filterChain, never()).doFilter(request, response);
    }
}
