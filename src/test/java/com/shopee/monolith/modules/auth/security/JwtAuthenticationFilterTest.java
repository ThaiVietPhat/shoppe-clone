package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SecurityErrorWriter securityErrorWriter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternalWhenNoAuthorizationHeaderShouldContinueChainAndNotAuthenticate() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalWhenHeaderDoesNotStartWithBearerShouldWriteInvalidTokenErrorAndAbort() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.INVALID_TOKEN);
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalWhenOAuth2ExchangeHasBadAuthorizationHeaderShouldContinueChain() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");
        when(request.getServletPath()).thenReturn("/api/auth/oauth2/exchange");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(securityErrorWriter, never()).writeError(response, ErrorCode.INVALID_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalWhenOAuth2ExchangeHasInvalidBearerTokenShouldContinueChain() throws ServletException, IOException {
        String token = "invalid.token.here";
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(request.getServletPath()).thenReturn("/api/auth/oauth2/exchange");
        when(jwtTokenProvider.parseAccessToken(token)).thenThrow(new AppException(ErrorCode.INVALID_TOKEN));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(securityErrorWriter, never()).writeError(response, ErrorCode.INVALID_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalWhenBearerTokenIsEmptyShouldWriteInvalidTokenErrorAndAbort() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer   ");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.INVALID_TOKEN);
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalWhenTokenIsValidShouldAuthenticateAndContinueChain() throws ServletException, IOException {
        String token = "valid.token.here";
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);

        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(UUID.randomUUID())
                .role(Role.BUYER)
                .jti("jti-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        when(jwtTokenProvider.parseAccessToken(token)).thenReturn(claims);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(claims, auth.getPrincipal());
        assertEquals("ROLE_BUYER", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void doFilterInternalWhenTokenIsInvalidShouldWriteErrorAndAbort() throws ServletException, IOException {
        String token = "invalid.token.here";
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(jwtTokenProvider.parseAccessToken(token)).thenThrow(new AppException(ErrorCode.INVALID_TOKEN));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.INVALID_TOKEN);
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
