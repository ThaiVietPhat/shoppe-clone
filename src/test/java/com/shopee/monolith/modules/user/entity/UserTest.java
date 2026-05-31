package com.shopee.monolith.modules.user.entity;

import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    @Test
    void builderWhenRoleAndStatusMissingShouldUseDefaults() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hash")
                .build();

        assertEquals(Role.BUYER, user.getRole());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
    }

    @Test
    void activateShouldSetStatusToActive() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hash")
                .build();

        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());

        user.activate();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }
}
