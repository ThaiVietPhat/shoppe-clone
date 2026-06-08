package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.user.dto.command.CreateVerificationTokenCommand;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.entity.VerificationToken;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.UserRepository;
import com.shopee.monolith.modules.user.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserVerificationServiceImpl implements UserVerificationService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void createVerificationToken(CreateVerificationTokenCommand command) {
        VerificationToken verificationToken = VerificationToken.builder()
                .userId(command.userId())
                .tokenHash(command.tokenHash())
                .expiresAt(command.expiresAt())
                .build();
        verificationTokenRepository.save(verificationToken);
    }

    @Override
    @Transactional
    public void verifyTokenHash(String tokenHash, Instant now) {
        VerificationToken verificationToken = verificationTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (verificationToken.isConsumed()) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_REUSED);
        }
        if (verificationToken.isExpired(now)) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        User user = userRepository.findByIdForUpdate(verificationToken.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        user.activate();
        verificationToken.consume(now);

        userRepository.save(user);
        verificationTokenRepository.save(verificationToken);
    }
}
