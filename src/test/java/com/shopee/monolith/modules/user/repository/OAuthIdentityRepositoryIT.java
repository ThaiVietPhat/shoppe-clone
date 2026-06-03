package com.shopee.monolith.modules.user.repository;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.user.entity.OAuthIdentity;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthIdentityRepositoryIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthIdentityRepository oauthIdentityRepository;

    @BeforeEach
    @AfterEach
    void cleanDb() {
        oauthIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveAndFindOAuthIdentityShouldSucceed() {
        User user = User.builder()
                .email("oauth-it@shopee.com")
                .normalizedEmail("oauth-it@shopee.com")
                .passwordHash(null) // Nullable password for OAuth
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        OAuthIdentity identity = OAuthIdentity.builder()
                .userId(savedUser.getId())
                .provider("google")
                .providerUserId("google_it_12345")
                .emailAtProvider("oauth-it@shopee.com")
                .build();

        OAuthIdentity savedIdentity = oauthIdentityRepository.saveAndFlush(identity);
        assertNotNull(savedIdentity.getId());

        Optional<OAuthIdentity> foundOpt = oauthIdentityRepository.findByProviderAndProviderUserId("google", "google_it_12345");
        assertTrue(foundOpt.isPresent());
        OAuthIdentity found = foundOpt.get();
        assertEquals(savedUser.getId(), found.getUserId());
        assertEquals("google", found.getProvider());
        assertEquals("google_it_12345", found.getProviderUserId());
    }

    @Test
    void saveDuplicateProviderAndProviderUserIdShouldThrowException() {
        User user1 = userRepository.saveAndFlush(User.builder()
                .email("user1@shopee.com")
                .normalizedEmail("user1@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build());

        User user2 = userRepository.saveAndFlush(User.builder()
                .email("user2@shopee.com")
                .normalizedEmail("user2@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build());

        oauthIdentityRepository.saveAndFlush(OAuthIdentity.builder()
                .userId(user1.getId())
                .provider("google")
                .providerUserId("google_dup")
                .build());

        OAuthIdentity dupIdentity = OAuthIdentity.builder()
                .userId(user2.getId())
                .provider("google")
                .providerUserId("google_dup")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            oauthIdentityRepository.saveAndFlush(dupIdentity);
        });
    }

    @Test
    void deleteUserShouldCascadeDeleteOAuthIdentity() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("cascade@shopee.com")
                .normalizedEmail("cascade@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build());

        OAuthIdentity identity = oauthIdentityRepository.saveAndFlush(OAuthIdentity.builder()
                .userId(user.getId())
                .provider("facebook")
                .providerUserId("fb_cascade")
                .build());

        assertTrue(oauthIdentityRepository.findById(identity.getId()).isPresent());

        // Delete user
        userRepository.delete(user);
        userRepository.flush();

        // Check identity is automatically cascade deleted
        assertFalse(oauthIdentityRepository.findById(identity.getId()).isPresent());
    }
}
