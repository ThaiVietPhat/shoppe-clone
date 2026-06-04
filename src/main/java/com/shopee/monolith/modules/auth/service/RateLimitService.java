package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties.BucketLimitProperties;
import com.shopee.monolith.modules.auth.dto.internal.RateLimitResult;

public interface RateLimitService {

    RateLimitResult consume(String key, BucketLimitProperties limitProperties);
}
