package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class EventPayloadCryptoServiceImpl implements EventPayloadCryptoService {

    private final AuthSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);

            AuthSecurityProperties.EventCryptoProperties cryptoProps = properties.getEventCrypto();
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(cryptoProps.getActiveSecret()), spec);
            byte[] cipherTextBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherTextBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherTextBytes, 0, combined, iv.length, cipherTextBytes.length);
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(combined);

            return cryptoProps.getActiveKeyId() + "." + payload;
        } catch (Exception e) {
            throw new IllegalStateException("Event payload encryption failed", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        int dotIndex = cipherText.indexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException("Invalid cipher text format");
        }

        String kid = cipherText.substring(0, dotIndex);
        String payload = cipherText.substring(dotIndex + 1);

        SecretKey key;
        try {
            key = getSecretKeyForKid(kid);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES key derivation failed", e);
        }

        try {
            byte[] combined = Base64.getUrlDecoder().decode(payload);
            if (combined.length < 12) {
                throw new IllegalArgumentException("Invalid cipher text length");
            }
            byte[] iv = new byte[12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] cipherTextBytes = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherTextBytes, 0, cipherTextBytes.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return new String(cipher.doFinal(cipherTextBytes), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Event payload decryption failed", e);
        }
    }

    private SecretKey getSecretKey(String secret) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private SecretKey getSecretKeyForKid(String kid) throws NoSuchAlgorithmException {
        AuthSecurityProperties.EventCryptoProperties cryptoProps = properties.getEventCrypto();
        if (cryptoProps.getActiveKeyId().equals(kid)) {
            return getSecretKey(cryptoProps.getActiveSecret());
        }
        if (cryptoProps.getPreviousKeyId() != null && cryptoProps.getPreviousKeyId().equals(kid)) {
            return getSecretKey(cryptoProps.getPreviousSecret());
        }
        throw new IllegalArgumentException("Unknown event crypto key ID: " + kid);
    }
}
