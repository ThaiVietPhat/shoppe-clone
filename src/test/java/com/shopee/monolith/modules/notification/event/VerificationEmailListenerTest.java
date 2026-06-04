package com.shopee.monolith.modules.notification.event;

import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.notification.config.NotificationProperties;
import com.shopee.monolith.modules.notification.service.EmailService;
import com.shopee.monolith.modules.user.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationEmailListenerTest {

    private EmailService emailService;
    private EventPayloadCryptoService cryptoService;
    private NotificationProperties properties;
    private VerificationEmailListener listener;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        cryptoService = mock(EventPayloadCryptoService.class);
        properties = new NotificationProperties();
        properties.setVerificationUrl("http://localhost:3000/verify-email");
        properties.setSender("no-reply@shoppe.local");

        listener = new VerificationEmailListener(emailService, cryptoService, properties);
    }

    @Test
    void whenUserRegisteredEventIsProcessedShouldDecryptAndSendEmail() {
        UUID userId = UUID.randomUUID();
        String encryptedToken = "kid.encryptedToken";
        String decryptedToken = "decryptedRawToken123";
        String email = "buyer@shoppe.local";

        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, encryptedToken);

        when(cryptoService.decrypt(encryptedToken)).thenReturn(decryptedToken);

        listener.handle(event);

        verify(cryptoService).decrypt(encryptedToken);
        verify(emailService).sendVerificationEmail(email, "http://localhost:3000/verify-email?token=" + decryptedToken);
    }

    @Test
    void whenEmailServiceFailsShouldRethrowException() {
        UUID userId = UUID.randomUUID();
        String encryptedToken = "kid.encryptedToken";
        String decryptedToken = "decryptedRawToken123";
        String email = "buyer@shoppe.local";

        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, encryptedToken);

        when(cryptoService.decrypt(encryptedToken)).thenReturn(decryptedToken);
        doThrow(new RuntimeException("SMTP Server Down"))
                .when(emailService).sendVerificationEmail(anyString(), anyString());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> listener.handle(event));
        assertTrue(exception.getMessage().contains("Email delivery failed for user"));
    }
}
