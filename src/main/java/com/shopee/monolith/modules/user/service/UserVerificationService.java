package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.modules.user.dto.command.CreateVerificationTokenCommand;

import java.time.Instant;

public interface UserVerificationService {

    void createVerificationToken(CreateVerificationTokenCommand command);

    void verifyTokenHash(String tokenHash, Instant now);
}
