package com.shopee.monolith.modules.auth.dto.internal;

public sealed interface RotationResult {
    record ReuseDetected() implements RotationResult {}
    record Rotated(IssuedTokenPair tokenPair) implements RotationResult {}
}
