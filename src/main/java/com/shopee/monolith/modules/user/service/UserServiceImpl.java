package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.user.dto.command.RegisterUserCommand;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.mapper.UserMapper;
import com.shopee.monolith.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        if (email == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    public Optional<UserAuthenticationData> findAuthenticationDataByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .map(userMapper::toAuthenticationData);
    }

    @Override
    public Optional<UserAuthenticationData> findAuthenticationDataById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id)
                .map(userMapper::toAuthenticationData);
    }

    @Override
    @Transactional
    public UserResponse registerUser(RegisterUserCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(command.passwordHash())
                .build();

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return userMapper.toResponse(savedUser);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional
    public void activateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.activate();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

