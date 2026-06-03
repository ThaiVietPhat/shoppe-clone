package com.shopee.monolith.common.security;

public interface EventPayloadCryptoService {
    String encrypt(String plainText);
    String decrypt(String cipherText);
}
