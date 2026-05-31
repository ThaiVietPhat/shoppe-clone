package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.modules.user.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {
    
    UserResponse getUserById(UUID id);
    
    UserResponse getUserByEmail(String email);

    com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData getAuthenticationDataByEmail(String email);
}
