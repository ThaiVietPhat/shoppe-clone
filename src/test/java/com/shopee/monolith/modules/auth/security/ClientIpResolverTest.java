package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    private AuthSecurityProperties properties;
    private ClientIpResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        properties = new AuthSecurityProperties();
        resolver = new ClientIpResolver(properties);
        request = Mockito.mock(HttpServletRequest.class);
    }

    @Test
    void whenNoTrustedProxiesConfiguredShouldUseRemoteAddr() {
        properties.setTrustedProxies(List.of());
        when(request.getRemoteAddr()).thenReturn("192.168.10.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        String ip = resolver.resolveIp(request);
        assertEquals("192.168.10.5", ip);
    }

    @Test
    void whenRemoteAddrMatchesTrustedProxyShouldExtractFirstXffIp() {
        properties.setTrustedProxies(List.of("192.168.10.0/24"));
        when(request.getRemoteAddr()).thenReturn("192.168.10.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 192.168.10.1");

        String ip = resolver.resolveIp(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void whenRemoteAddrDoesNotMatchTrustedProxyShouldUseRemoteAddr() {
        properties.setTrustedProxies(List.of("192.168.10.0/24"));
        when(request.getRemoteAddr()).thenReturn("172.16.5.4");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");

        String ip = resolver.resolveIp(request);
        assertEquals("172.16.5.4", ip);
    }

    @Test
    void whenXffHeaderIsEmptyShouldFallbackToRemoteAddr() {
        properties.setTrustedProxies(List.of("192.168.10.0/24"));
        when(request.getRemoteAddr()).thenReturn("192.168.10.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("");

        String ip = resolver.resolveIp(request);
        assertEquals("192.168.10.5", ip);
    }

    @Test
    void shouldNormalizeIPv6Literals() {
        properties.setTrustedProxies(List.of());
        
        // Loopback IPv6
        when(request.getRemoteAddr()).thenReturn("::1");
        assertEquals("0:0:0:0:0:0:0:1", resolver.resolveIp(request));

        // Bracketed loopback
        when(request.getRemoteAddr()).thenReturn("[::1]");
        assertEquals("0:0:0:0:0:0:0:1", resolver.resolveIp(request));

        // Compressed IPv6
        when(request.getRemoteAddr()).thenReturn("2001:db8::1");
        assertEquals("2001:db8:0:0:0:0:0:1", resolver.resolveIp(request));
    }

    @Test
    void whenXffHeaderContainsInvalidIpShouldFallbackToRemoteAddr() {
        properties.setTrustedProxies(List.of("192.168.10.0/24"));
        when(request.getRemoteAddr()).thenReturn("192.168.10.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("invalid_ip_format, 192.168.10.1");

        String ip = resolver.resolveIp(request);
        assertEquals("192.168.10.5", ip);
    }
}
