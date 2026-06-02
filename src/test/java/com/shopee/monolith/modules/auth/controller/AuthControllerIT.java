package com.shopee.monolith.modules.auth.controller;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthSecurityProperties properties;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void csrfWhenGetCsrfEndpointShouldSetCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginWhenCsrfTokenMissingShouldReturn403Forbidden() throws Exception {
        String loginPayload = "{\"email\":\"test@example.com\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWhenCsrfTokenPresentShouldPassCsrfFilter() throws Exception {
        String loginPayload = "{\"email\":\"test@example.com\",\"password\":\"password123\"}";

        // 1. Fetch CSRF token
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpServletResponse csrfResponse = csrfResult.getResponse();
        var csrfCookie = csrfResponse.getCookie(properties.getCsrf().getCookieName());
        assertNotNull(csrfCookie);

        // 2. Post login with CSRF cookie and header
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload)
                        .cookie(csrfCookie)
                        .header(properties.getCsrf().getHeaderName(), csrfCookie.getValue()))
                // Since user test@example.com doesn't exist, we expect 401 INVALID_CREDENTIALS,
                // which proves the request successfully passed the CSRF filter!
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsWhenAllowedOriginShouldReturnCorsHeaders() throws Exception {
        String origin = properties.getCors().getAllowedOrigins().get(0);

        mockMvc.perform(get("/api/auth/csrf")
                        .header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsWhenDisallowedOriginShouldNotReturnCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .header(HttpHeaders.ORIGIN, "http://malicious-attacker.com"))
                .andExpect(status().isForbidden())
                // In Spring Security, if an origin is disallowed, Access-Control-Allow-Origin is not set or request is rejected
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void actuatorInfoShouldBeSecured() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealthShouldBePublic() throws Exception {
        // Since database/redis health indicators might be DOWN or UP in test, we just check liveness/readiness endpoint
        int livenessStatus = mockMvc.perform(get("/actuator/health/liveness"))
                .andReturn().getResponse().getStatus();
        // Since probe is public, it should return 200 (or 503 if DOWN), but definitely NOT 401/403.
        assertEquals(200, livenessStatus);
    }
}
