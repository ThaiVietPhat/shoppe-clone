package com.shopee.monolith.modules.auth.dto.internal;

public record RateLimitResult(boolean allowed, long remainingTokens) {}
