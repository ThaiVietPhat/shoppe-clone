package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.request.LoginRequest;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    // Dummy BCrypt hash of cost 12 to run timing-equivalent check for non-existent users
    private static final String DUMMY_HASH = "$2a$12$6yGZ/X4sF.FhPUp1p.2KFeZpG.0u4hW1.c.4zY5P6q7r8s9t0u1v2";

    @Override
    public IssuedTokenPair login(LoginRequest request) {
        Optional<UserAuthenticationData> userAuthDataOpt = userService.findAuthenticationDataByEmail(request.email());

        String passwordHash = userAuthDataOpt.map(UserAuthenticationData::passwordHash).orElse(null);
        boolean userExists = userAuthDataOpt.isPresent() && passwordHash != null;

        String hashToMatch = userExists ? passwordHash : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToMatch);

        if (!userExists || !passwordMatches) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserAuthenticationData userAuthData = userAuthDataOpt.get();
        validateUserStatus(userAuthData.status());

        return refreshTokenService.issueTokenPair(userAuthData.id(), userAuthData.role());
    }

    private void validateUserStatus(UserStatus status) {
        if (status == UserStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (status != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }
}
