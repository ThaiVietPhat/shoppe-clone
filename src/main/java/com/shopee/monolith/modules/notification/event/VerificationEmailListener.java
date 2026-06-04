package com.shopee.monolith.modules.notification.event;

import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.notification.config.NotificationProperties;
import com.shopee.monolith.modules.notification.service.EmailService;
import com.shopee.monolith.modules.user.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailListener {

    private final EmailService emailService;
    private final EventPayloadCryptoService cryptoService;
    private final NotificationProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for userId: {}", event.userId());
        try {
            // Decrypt raw verification token
            String rawToken = cryptoService.decrypt(event.encryptedVerificationToken());

            // Build link: {verification-url}?token={rawToken}
            String verificationLink = properties.getVerificationUrl() + "?token=" + rawToken;

            // Send email
            emailService.sendVerificationEmail(event.email(), verificationLink);
            log.info("Successfully processed verification email for userId: {}", event.userId());
        } catch (Exception e) {
            log.error("Failed to process verification email for userId: {}", event.userId());
            // Rethrow so Modulith leaves the publication log in an incomplete state
            throw new RuntimeException("Email delivery failed for user " + event.userId(), e);
        }
    }
}
