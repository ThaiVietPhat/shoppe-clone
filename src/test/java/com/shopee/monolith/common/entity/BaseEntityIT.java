package com.shopee.monolith.common.entity;

import com.shopee.monolith.BaseIntegrationTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
@Transactional
class BaseEntityIT extends BaseIntegrationTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldAutoGenerateUuidAndAuditTimestamps() {
        // Arrange
        DummyEntity dummy = new DummyEntity();
        dummy.setName("Test Entity");

        // Act
        entityManager.persist(dummy);
        entityManager.flush();
        DummyEntity saved = dummy;

        // Assert
        assertNotNull(saved.getId(), "UUID should be automatically generated");
        assertNotNull(saved.getCreatedAt(), "createdAt should be automatically populated by JPA Auditing");
        assertNotNull(saved.getUpdatedAt(), "updatedAt should be automatically populated by JPA Auditing");
        
        // Also assert that the generated ID is a valid UUID version 4 or at least string-length correct
        assertTrue(saved.getId().toString().length() > 30, "UUID string length is invalid");
    }

    @Entity
    @Table(name = "dummy_entities")
    @Getter
    @Setter
    public static class DummyEntity extends BaseEntity {
        private String name;
    }
}
