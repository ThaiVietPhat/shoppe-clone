package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.mapper.UserMapper;
import com.shopee.monolith.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UserResponse testResponse;
    private final UUID testId = UUID.randomUUID();
    private final String testEmail = "test@shopee.com";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(testId)
                .email(testEmail)
                .role(Role.BUYER)
                .build();
                
        testResponse = UserResponse.builder()
                .id(testId)
                .email(testEmail)
                .role(Role.BUYER)
                .build();
    }

    @Test
    void getUserByIdWhenUserExistsShouldReturnUser() {
        when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        UserResponse result = userService.getUserById(testId);

        assertEquals(testResponse, result);
    }

    @Test
    void getUserByIdWhenUserDoesNotExistShouldThrowException() {
        when(userRepository.findById(testId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.getUserById(testId));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getUserByEmailWhenUserExistsShouldReturnUser() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        UserResponse result = userService.getUserByEmail(testEmail);

        assertEquals(testResponse, result);
    }

    @Test
    void getUserByEmailWhenUserDoesNotExistShouldThrowException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.getUserByEmail(testEmail));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getAuthenticationDataByEmailWhenUserExistsShouldReturnAuthData() {
        com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData authData = 
                com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData.builder()
                        .id(testId)
                        .email(testEmail)
                        .passwordHash("hashedPwd")
                        .role(Role.BUYER)
                        .status(com.shopee.monolith.modules.user.model.UserStatus.PENDING_VERIFICATION)
                        .build();
                
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(userMapper.toAuthenticationData(testUser)).thenReturn(authData);

        com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData result = userService.getAuthenticationDataByEmail(testEmail);

        assertEquals(authData, result);
    }

    @Test
    void getAuthenticationDataByEmailWhenUserDoesNotExistShouldThrowException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.getAuthenticationDataByEmail(testEmail));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}
