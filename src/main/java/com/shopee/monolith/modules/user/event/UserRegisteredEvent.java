package com.shopee.monolith.modules.user.event;

import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        String encryptedVerificationToken
) {
    @Override
    public String toString() {
        return "UserRegisteredEvent[userId=" + userId + ", email=" + email + ", encryptedVerificationToken=[REDACTED]]";
    }
}
