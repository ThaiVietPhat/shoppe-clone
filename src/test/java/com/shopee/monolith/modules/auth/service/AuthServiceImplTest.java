package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.request.LoginRequest;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private com.shopee.monolith.modules.user.repository.VerificationTokenRepository verificationTokenRepository;

    @Mock
    private com.shopee.monolith.modules.auth.security.VerificationTokenGenerator verificationTokenGenerator;

    @Mock
    private EventPayloadCryptoService eventPayloadCryptoService;

    @Mock
    private com.shopee.monolith.modules.user.repository.UserRepository userRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.shopee.monolith.modules.auth.config.AuthSecurityProperties securityProperties;

    @Mock
    private java.time.Clock clock;

    private AuthServiceImpl authService;

    private static final String DUMMY_HASH = AuthServiceImpl.DUMMY_HASH;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userService,
                passwordEncoder,
                refreshTokenService,
                verificationTokenRepository,
                verificationTokenGenerator,
                eventPayloadCryptoService,
                userRepository,
                eventPublisher,
                securityProperties,
                clock
        );
    }

    @Test
    void loginActiveUserShouldReturnTokenPairAndDelegateToRefreshTokenService() {
        UUID userId = UUID.randomUUID();
        String email = "test@shopee.com";
        String password = "validPassword";
        String passwordHash = "hashedPassword";
        String mockAccessToken = "mock.jwt.access.token";
        String mockRawRefreshToken = "mockRawRefreshToken";

        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(userId)
                .email(email)
                .passwordHash(passwordHash)
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);

        IssuedTokenPair expectedPair = IssuedTokenPair.builder()
                .accessToken(mockAccessToken)
                .refreshToken(mockRawRefreshToken)
                .build();
        when(refreshTokenService.issueTokenPair(userId, Role.BUYER)).thenReturn(expectedPair);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        IssuedTokenPair tokenPair = authService.login(request);

        assertNotNull(tokenPair);
        assertEquals(mockAccessToken, tokenPair.accessToken());
        assertEquals(mockRawRefreshToken, tokenPair.refreshToken());

        verify(refreshTokenService).issueTokenPair(userId, Role.BUYER);
    }

    @Test
    void loginWithNonExistentEmailShouldThrowInvalidCredentialsExceptionAndRunPasswordEncoderWithDummyHash() {
        String email = "unknown@shopee.com";
        String password = "anyPassword";
        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(password, DUMMY_HASH)).thenReturn(false);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());

        verify(passwordEncoder).matches(password, DUMMY_HASH);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginWithWrongPasswordShouldThrowInvalidCredentialsException() {
        String email = "test@shopee.com";
        String password = "wrongPassword";
        String passwordHash = "hashedPassword";

        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(false);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginOAuth2UserWithNullPasswordHashShouldThrowInvalidCredentialsExceptionAndRunPasswordEncoderWithDummyHash() {
        String email = "oauth2@shopee.com";
        String password = "somePassword";

        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(null)
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches(password, DUMMY_HASH)).thenReturn(false);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());

        verify(passwordEncoder).matches(password, DUMMY_HASH);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginPendingVerificationUserShouldThrowEmailNotVerifiedException() {
        String email = "pending@shopee.com";
        String password = "validPassword";
        String passwordHash = "hashedPassword";

        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .role(Role.BUYER)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, exception.getErrorCode());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginInactiveUserShouldThrowAccountNotActiveException() {
        String email = "inactive@shopee.com";
        String password = "validPassword";
        String passwordHash = "hashedPassword";

        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .role(Role.BUYER)
                .status(UserStatus.INACTIVE)
                .build();

        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, exception.getErrorCode());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginLockedUserShouldThrowAccountNotActiveException() {
        String email = "locked@shopee.com";
        String password = "validPassword";
        String passwordHash = "hashedPassword";

        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .role(Role.BUYER)
                .status(UserStatus.LOCKED)
                .build();

        when(userService.findAuthenticationDataByEmail(email)).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, exception.getErrorCode());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void dummyHashShouldBeValidBCryptFormat() {
        assertNotNull(AuthServiceImpl.DUMMY_HASH);
        assertEquals(60, AuthServiceImpl.DUMMY_HASH.length());
        org.junit.jupiter.api.Assertions.assertTrue(
                AuthServiceImpl.DUMMY_HASH.startsWith("$2a$12$") ||
                AuthServiceImpl.DUMMY_HASH.startsWith("$2b$12$") ||
                AuthServiceImpl.DUMMY_HASH.startsWith("$2y$12$")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                java.util.regex.Pattern.matches("^\\$2[abyd]\\$\\d{2}\\$[./A-Za-z0-9]{53}$", AuthServiceImpl.DUMMY_HASH)
        );
    }
}
