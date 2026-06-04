package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.dto.internal.RateLimitResult;
import com.shopee.monolith.modules.auth.service.RateLimitService;
import com.shopee.monolith.modules.user.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitingFilterTest {

    private RateLimitService rateLimitService;
    private ClientIpResolver ipResolver;
    private AuthSecurityProperties properties;
    private SecurityErrorWriter securityErrorWriter;
    private FilterChain filterChain;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        ipResolver = mock(ClientIpResolver.class);
        properties = new AuthSecurityProperties();
        securityErrorWriter = mock(SecurityErrorWriter.class);
        filterChain = mock(FilterChain.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        filter = new RateLimitingFilter(rateLimitService, ipResolver, properties, securityErrorWriter);
        when(ipResolver.resolveIp(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whenRateLimiterIsDisabledShouldBypass() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService, never()).consume(any(), any());
    }

    @Test
    void whenPathIsHealthCheckShouldBypass() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/actuator/health/readiness");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService, never()).consume(any(), any());
    }

    @Test
    void whenAnonymousRequestIsAllowedShouldProceed() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/api/public-endpoint");
        when(request.getMethod()).thenReturn("GET");
        when(rateLimitService.consume(eq("rate_limit:ip:127.0.0.1"), any()))
                .thenReturn(new RateLimitResult(true, 99));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenAnonymousRequestIsRateLimitedShouldReturn429() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/api/public-endpoint");
        when(request.getMethod()).thenReturn("GET");
        when(rateLimitService.consume(eq("rate_limit:ip:127.0.0.1"), any()))
                .thenReturn(new RateLimitResult(false, 0));

        filter.doFilter(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.RATE_LIMIT_EXCEEDED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void whenAuthenticatedRequestShouldUseUserBucket() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null, null)
        );

        when(request.getServletPath()).thenReturn("/api/orders");
        when(request.getMethod()).thenReturn("POST");
        when(rateLimitService.consume(eq("rate_limit:user:" + userId), any()))
                .thenReturn(new RateLimitResult(true, 299));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenLoginRequestShouldUseLoginSpecificBucket() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(rateLimitService.consume(eq("rate_limit:auth:login:127.0.0.1"), eq(properties.getRateLimit().getLogin())))
                .thenReturn(new RateLimitResult(true, 9));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenOauthExchangeRequestShouldUseOauthBucket() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/api/auth/oauth2/exchange");
        when(request.getMethod()).thenReturn("POST");
        when(rateLimitService.consume(eq("rate_limit:oauth2:callback:exchange:127.0.0.1"), eq(properties.getRateLimit().getOauth2Callback())))
                .thenReturn(new RateLimitResult(true, 29));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenOAuth2CallbackCodeShouldUseOauthBucketWithProvider() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/login/oauth2/code/google");
        when(request.getMethod()).thenReturn("GET");
        when(rateLimitService.consume(eq("rate_limit:oauth2:callback:google:127.0.0.1"), eq(properties.getRateLimit().getOauth2Callback())))
                .thenReturn(new RateLimitResult(true, 29));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenRedisFailsOnProtectedRequestShouldFailClosed() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder().userId(userId).role(Role.BUYER).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null, null)
        );

        when(request.getServletPath()).thenReturn("/api/orders");
        when(request.getMethod()).thenReturn("POST");
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        filter.doFilter(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.SERVICE_UNAVAILABLE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void whenRedisFailsOnAbuseControlEndpointShouldFailClosed() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        filter.doFilter(request, response, filterChain);

        verify(securityErrorWriter).writeError(response, ErrorCode.SERVICE_UNAVAILABLE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void whenRedisFailsOnLogoutShouldFailOpen() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder().userId(userId).role(Role.BUYER).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null, null)
        );

        when(request.getServletPath()).thenReturn("/api/auth/logout");
        when(request.getMethod()).thenReturn("POST");
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(securityErrorWriter, never()).writeError(any(), any());
    }

    @Test
    void whenRedisFailsOnNormalPublicEndpointShouldFailOpen() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/api/public-endpoint");
        when(request.getMethod()).thenReturn("GET");
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(securityErrorWriter, never()).writeError(any(), any());
    }

    @Test
    void whenOAuth2CallbackCodeWithUnknownProviderShouldCollapseToUnknownBucket() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(true);
        when(request.getServletPath()).thenReturn("/login/oauth2/code/unknown-provider");
        when(request.getMethod()).thenReturn("GET");
        when(rateLimitService.consume(eq("rate_limit:oauth2:callback:unknown:127.0.0.1"), eq(properties.getRateLimit().getOauth2Callback())))
                .thenReturn(new RateLimitResult(true, 29));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
