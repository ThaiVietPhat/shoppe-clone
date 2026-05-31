package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.request.LoginRequest;

public interface AuthService {
    IssuedTokenPair login(LoginRequest request);
}
