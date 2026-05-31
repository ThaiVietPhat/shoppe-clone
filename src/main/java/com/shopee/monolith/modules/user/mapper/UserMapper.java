package com.shopee.monolith.modules.user.mapper;

import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    
    UserResponse toResponse(User user);

    @org.mapstruct.Mapping(source = "passwordHash", target = "passwordHash")
    com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData toAuthenticationData(User user);
}
