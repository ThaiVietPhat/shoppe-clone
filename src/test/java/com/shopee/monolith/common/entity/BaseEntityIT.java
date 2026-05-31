package com.shopee.monolith.common.entity;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BaseEntityIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldAutoGenerateUuidAndAuditTimestampsOnCreateAndUpdate() {
        // --- 1. CREATE ---
        User user = User.builder()
                .email("test.audit." + UUID.randomUUID() + "@example.com")
                .role(Role.BUYER)
                .status(com.shopee.monolith.modules.user.model.UserStatus.PENDING_VERIFICATION)
                .build();

        entityManager.persist(user);
        entityManager.flush();
        
        assertNotNull(user.getId(), "UUID should be automatically generated");
        assertNotNull(user.getCreatedAt(), "createdAt should be automatically populated");
        assertNotNull(user.getUpdatedAt(), "updatedAt should be automatically populated");
        
        Instant initialCreatedAt = user.getCreatedAt();
        Instant initialUpdatedAt = user.getUpdatedAt();

        // --- 2. UPDATE ---
        // Clear context to ensure it fetches from DB
        entityManager.clear();

        // Wait a few milliseconds to ensure timestamp differs
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        User loaded = entityManager.find(User.class, user.getId());
        org.springframework.test.util.ReflectionTestUtils.setField(loaded, "email", "updated." + UUID.randomUUID() + "@example.com"); // update a field

        entityManager.persist(loaded);
        entityManager.flush();

        // --- 3. ASSERT ---
        assertEquals(initialCreatedAt.toEpochMilli(), loaded.getCreatedAt().toEpochMilli(), "createdAt should NOT change on update");
        assertTrue(loaded.getUpdatedAt().isAfter(initialUpdatedAt), "updatedAt should strictly move forward");
    }

    @Test
    void shouldBeEqualWhenComparedToHibernateProxy() {
        User user = User.builder()
                .email("test.proxy." + UUID.randomUUID() + "@example.com")
                .role(Role.BUYER)
                .status(com.shopee.monolith.modules.user.model.UserStatus.ACTIVE)
                .build();

        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        // Get actual instance
        User actual = entityManager.find(User.class, user.getId());
        entityManager.clear();

        // Get proxy
        User proxy = entityManager.getReference(User.class, user.getId());

        // Validate that proxy is a proxy
        assertTrue(proxy instanceof org.hibernate.proxy.HibernateProxy, "Should be a Hibernate proxy");
        
        // Assert equality works both ways
        assertEquals(actual, proxy, "Actual should equal Proxy");
        assertEquals(proxy, actual, "Proxy should equal Actual");
        assertEquals(actual.hashCode(), proxy.hashCode(), "HashCodes should match");
    }
}
