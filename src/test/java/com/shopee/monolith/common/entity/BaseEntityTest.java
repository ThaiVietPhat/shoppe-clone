package com.shopee.monolith.common.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityTest {

    static class EntityA extends BaseEntity {
        EntityA(UUID id) {
            super(id, null, null);
        }
    }

    static class EntityB extends BaseEntity {
        EntityB(UUID id) {
            super(id, null, null);
        }
    }

    @Test
    void equalsWhenSameInstanceShouldBeTrue() {
        EntityA entity1 = new EntityA(null);
        assertEquals(entity1, entity1);
    }

    @Test
    void equalsWhenIdsAreNullShouldBeFalse() {
        EntityA entity1 = new EntityA(null);
        EntityA entity2 = new EntityA(null);
        assertNotEquals(entity1, entity2);
    }

    @Test
    void equalsWhenIdsAreDifferentShouldBeFalse() {
        EntityA entity1 = new EntityA(UUID.randomUUID());
        EntityA entity2 = new EntityA(UUID.randomUUID());
        assertNotEquals(entity1, entity2);
    }

    @Test
    void equalsWhenIdsAreSameButDifferentTypesShouldBeFalse() {
        UUID sharedId = UUID.randomUUID();
        EntityA entity1 = new EntityA(sharedId);
        EntityB entity2 = new EntityB(sharedId);
        assertNotEquals(entity1, entity2);
    }

    @Test
    void equalsWhenIdsAreSameAndSameTypeShouldBeTrue() {
        UUID sharedId = UUID.randomUUID();
        EntityA entity1 = new EntityA(sharedId);
        EntityA entity2 = new EntityA(sharedId);
        assertEquals(entity1, entity2);
    }

    @Test
    void hashCodeShouldBeConstantForClass() {
        EntityA entity1 = new EntityA(null);
        EntityA entity2 = new EntityA(UUID.randomUUID());
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    void hashSetShouldNotLoseEntityAfterIdIsSet() {
        Set<BaseEntity> set = new HashSet<>();
        
        EntityA entity = new EntityA(null);
        set.add(entity);
        assertTrue(set.contains(entity));
        
        // Simulating persist where ID gets set via reflection or subclass
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        
        // Assert that the entity can still be found in the HashSet
        assertTrue(set.contains(entity));
    }
}
