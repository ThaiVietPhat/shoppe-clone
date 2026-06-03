package com.shopee.monolith.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.dto.request.RegisterRequest;
import com.shopee.monolith.modules.auth.dto.request.VerifyRequest;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.user.entity.VerificationToken;
import com.shopee.monolith.modules.user.event.UserRegisteredEvent;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.UserRepository;
import com.shopee.monolith.modules.user.repository.VerificationTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AuthControllerRegistrationIT.EventCaptureConfig.class)
class AuthControllerRegistrationIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthSecurityProperties properties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private com.shopee.monolith.modules.auth.security.VerificationTokenGenerator verificationTokenGenerator;

    @Autowired
    private EventPayloadCryptoService eventPayloadCryptoService;

    @Autowired
    private ObjectMapper objectMapper;

    private static volatile UserRegisteredEvent lastEvent;

    @TestConfiguration
    static class EventCaptureConfig {
        @EventListener
        public void handleUserRegistered(UserRegisteredEvent event) {
            lastEvent = event;
        }
    }

    @BeforeEach
    @AfterEach
    void cleanDb() {
        lastEvent = null;
        List<String> testEmails = List.of(
                "phatdz@vietnam.com",
                "concurrent@shopee.com",
                "verify@shopee.com",
                "expired@shopee.com",
                "reused@shopee.com",
                "race@shopee.com"
        );
        for (String email : testEmails) {
            userRepository.findByNormalizedEmail(email).ifPresent(user -> {
                userRepository.delete(user);
            });
        }
        SecurityContextHolder.clearContext();
    }

    private MvcResult performPostWithCsrf(String url, Object body) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie(properties.getCsrf().getCookieName());
        assertNotNull(csrfCookie);

        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .cookie(csrfCookie)
                        .header(properties.getCsrf().getHeaderName(), csrfCookie.getValue()))
                .andReturn();
    }

    @Test
    void registerUserSuccessShouldPreserveEmailCaseSaveNormalizedEmailAndOpaqueTokenHash() throws Exception {
        String email = "PhatDZ@VietNam.com";
        String normalizedEmail = "phatdz@vietnam.com";
        RegisterRequest request = new RegisterRequest(email, "myPassword123");

        MvcResult result = performPostWithCsrf("/api/auth/register", request);
        assertEquals(200, result.getResponse().getStatus());

        // Verify database state
        Optional<User> userOpt = userRepository.findByNormalizedEmail(normalizedEmail);
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertEquals(email, user.getEmail());
        assertEquals(normalizedEmail, user.getNormalizedEmail());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());

        // Verify verification token event was triggered with encrypted payload
        assertNotNull(lastEvent);
        assertEquals(user.getId(), lastEvent.userId());
        assertEquals(email, lastEvent.email());
        assertNotNull(lastEvent.encryptedVerificationToken());
        assertTrue(lastEvent.toString().contains("encryptedVerificationToken=[REDACTED]"));

        // Decrypt raw token from event payload
        String rawToken = eventPayloadCryptoService.decrypt(lastEvent.encryptedVerificationToken());
        assertNotNull(rawToken);

        // Verify only token hash is saved in DB and raw/encrypted tokens are not stored in plaintext
        List<VerificationToken> tokens = verificationTokenRepository.findAll();
        assertEquals(1, tokens.size());
        VerificationToken token = tokens.get(0);
        assertEquals(user.getId(), token.getUserId());
        assertEquals(verificationTokenGenerator.hash(rawToken), token.getTokenHash());
        assertFalse(token.getTokenHash().contains(rawToken));
        assertFalse(token.getTokenHash().contains(lastEvent.encryptedVerificationToken()));
    }

    @Test
    void registerUserDuplicateEmailRaceShouldEnsureOnlyOneSucceeds() throws Exception {
        String email = "concurrent@shopee.com";
        RegisterRequest request = new RegisterRequest(email, "password123");

        int concurrency = 4;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    return performPostWithCsrf("/api/auth/register", request);
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        try {
            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (Future<MvcResult> future : futures) {
            MvcResult res = future.get();
            int status = res.getResponse().getStatus();
            String body = res.getResponse().getContentAsString();
            if (status == 200) {
                successCount.incrementAndGet();
            } else if (status == 409) {
                conflictCount.incrementAndGet();
                assertTrue(body.contains(ErrorCode.EMAIL_ALREADY_EXISTS.getMessage()));
            }
        }

        assertEquals(1, successCount.get(), "Only one registration must succeed");
        assertEquals(concurrency - 1, conflictCount.get(), "Remaining threads must get duplicate conflict");
    }

    @Test
    void verifySuccessShouldActivateUserAndConsumeToken() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("verify@shopee.com", "password123");
        performPostWithCsrf("/api/auth/register", registerReq);

        assertNotNull(lastEvent);
        String rawToken = eventPayloadCryptoService.decrypt(lastEvent.encryptedVerificationToken());

        VerifyRequest verifyReq = new VerifyRequest(rawToken);
        MvcResult result = performPostWithCsrf("/api/auth/verify", verifyReq);
        assertEquals(200, result.getResponse().getStatus());

        User user = userRepository.findByNormalizedEmail("verify@shopee.com").orElseThrow();
        assertEquals(UserStatus.ACTIVE, user.getStatus());

        VerificationToken token = verificationTokenRepository.findAll().get(0);
        assertTrue(token.isConsumed());
    }

    @Test
    void verifyWithExpiredTokenShouldFail() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("expired@shopee.com", "password123");
        performPostWithCsrf("/api/auth/register", registerReq);

        // Fetch verification token, update its expiresAt to past and save using same ID
        VerificationToken token = verificationTokenRepository.findAll().get(0);
        VerificationToken expiredToken = VerificationToken.builder()
                .id(token.getId())
                .userId(token.getUserId())
                .tokenHash(token.getTokenHash())
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        verificationTokenRepository.save(expiredToken);

        String rawToken = eventPayloadCryptoService.decrypt(lastEvent.encryptedVerificationToken());
        VerifyRequest verifyReq = new VerifyRequest(rawToken);
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isForbidden()); // Missing CSRF returns 403 Forbidden as expected
        
        MvcResult result = performPostWithCsrf("/api/auth/verify", verifyReq);
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains(ErrorCode.VERIFICATION_TOKEN_EXPIRED.getMessage()));
    }

    @Test
    void verifyWithReusedTokenShouldFail() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("reused@shopee.com", "password123");
        performPostWithCsrf("/api/auth/register", registerReq);

        String rawToken = eventPayloadCryptoService.decrypt(lastEvent.encryptedVerificationToken());
        VerifyRequest verifyReq = new VerifyRequest(rawToken);

        // First verification succeeds
        MvcResult res1 = performPostWithCsrf("/api/auth/verify", verifyReq);
        assertEquals(200, res1.getResponse().getStatus());

        // Second verification fails with token reused
        MvcResult res2 = performPostWithCsrf("/api/auth/verify", verifyReq);
        assertEquals(400, res2.getResponse().getStatus());
        assertTrue(res2.getResponse().getContentAsString().contains(ErrorCode.VERIFICATION_TOKEN_REUSED.getMessage()));
    }

    @Test
    void verifyWithInvalidTokenShouldFail() throws Exception {
        VerifyRequest verifyReq = new VerifyRequest("some_invalid_nonexistent_token");
        MvcResult result = performPostWithCsrf("/api/auth/verify", verifyReq);
        assertEquals(401, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains(ErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void doubleVerifyRaceShouldEnsureOnlyOneThreadSucceeds() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("race@shopee.com", "password123");
        performPostWithCsrf("/api/auth/register", registerReq);

        String rawToken = eventPayloadCryptoService.decrypt(lastEvent.encryptedVerificationToken());
        VerifyRequest verifyReq = new VerifyRequest(rawToken);

        int concurrency = 4;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    return performPostWithCsrf("/api/auth/verify", verifyReq);
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        try {
            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (Future<MvcResult> future : futures) {
            MvcResult res = future.get();
            int status = res.getResponse().getStatus();
            if (status == 200) {
                successCount.incrementAndGet();
            } else if (status == 400) {
                failureCount.incrementAndGet();
                assertTrue(res.getResponse().getContentAsString().contains(ErrorCode.VERIFICATION_TOKEN_REUSED.getMessage()));
            }
        }

        assertEquals(1, successCount.get(), "Only one verification request must succeed");
        assertEquals(concurrency - 1, failureCount.get(), "Remaining verification requests must get REUSED failure");
    }
}
