package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPayloadCryptoServiceImplTest {

    private AuthSecurityProperties properties;
    private EventPayloadCryptoServiceImpl cryptoService;

    @BeforeEach
    void setUp() {
        properties = new AuthSecurityProperties();
        // Setup default valid active secret (at least 32 bytes)
        properties.getEventCrypto().setActiveKeyId("crypto-v1");
        properties.getEventCrypto().setActiveSecret("default-event-crypto-secret-32b-key-default-value");
        cryptoService = new EventPayloadCryptoServiceImpl(properties);
    }

    @Test
    void whenEncryptAndDecryptWithActiveKeyShouldSucceed() {
        String plainText = "MySuperSecretVerificationToken";
        String cipherText = cryptoService.encrypt(plainText);

        assertNotNull(cipherText);
        assertTrue(cipherText.startsWith("crypto-v1."));

        String decrypted = cryptoService.decrypt(cipherText);
        assertEquals(plainText, decrypted);
    }

    @Test
    void whenKeyRotatesShouldStillDecryptUsingPreviousKey() {
        String plainText = "OldVerificationTokenFromPreviousKey";
        String cipherText = cryptoService.encrypt(plainText);
        assertTrue(cipherText.startsWith("crypto-v1."));

        // Rotate keys: crypto-v1 becomes previous, crypto-v2 becomes active
        properties.getEventCrypto().setPreviousKeyId("crypto-v1");
        properties.getEventCrypto().setPreviousSecret("default-event-crypto-secret-32b-key-default-value");

        properties.getEventCrypto().setActiveKeyId("crypto-v2");
        properties.getEventCrypto().setActiveSecret("new-active-crypto-secret-key-at-least-32-bytes");

        // Attempt decryption of the old ciphertext
        String decrypted = cryptoService.decrypt(cipherText);
        assertEquals(plainText, decrypted);

        // Verify new encryption uses the new active key ID
        String newCipherText = cryptoService.encrypt("someNewToken");
        assertTrue(newCipherText.startsWith("crypto-v2."));
    }

    @Test
    void whenDecryptingWithUnknownKeyIdShouldThrowException() {
        String invalidCipherText = "crypto-unknown.dGhpcyBpcyBteSBjaXBoZXI=";
        assertThrows(IllegalArgumentException.class, () -> cryptoService.decrypt(invalidCipherText));
    }

    @Test
    void whenDecryptingMalformedCipherTextShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> cryptoService.decrypt("malformed-no-dot"));
    }

    @Test
    void whenEncryptNullOrBlankShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> cryptoService.encrypt(null));
        assertThrows(IllegalArgumentException.class, () -> cryptoService.encrypt(""));
        assertThrows(IllegalArgumentException.class, () -> cryptoService.encrypt("   "));
    }

    @Test
    void whenDecryptNullOrBlankShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> cryptoService.decrypt(null));
        assertThrows(IllegalArgumentException.class, () -> cryptoService.decrypt(""));
        assertThrows(IllegalArgumentException.class, () -> cryptoService.decrypt("   "));
    }
}
