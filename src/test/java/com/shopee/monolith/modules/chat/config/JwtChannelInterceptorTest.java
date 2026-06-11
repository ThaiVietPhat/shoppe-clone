package com.shopee.monolith.modules.chat.config;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.service.AccessTokenBlacklistService;
import com.shopee.monolith.modules.chat.service.ChatService;
import com.shopee.monolith.modules.user.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AccessTokenBlacklistService blacklistService;
    @Mock
    private ObjectProvider<ChatService> chatServiceProvider;
    @Mock
    private ChatService chatService;
    @Mock
    private MessageChannel channel;

    private JwtChannelInterceptor interceptor;

    private UUID userId;
    private AccessTokenClaims claims;

    @BeforeEach
    void setUp() {
        interceptor = new JwtChannelInterceptor(jwtTokenProvider, blacklistService, chatServiceProvider);
        userId = UUID.randomUUID();
        claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .jti("jti-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        lenient().when(chatServiceProvider.getObject()).thenReturn(chatService);
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    claims, null, List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSendConnectWithoutTokenShouldReject() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void preSendConnectWithValidTokenShouldAttachPrincipal() {
        when(jwtTokenProvider.parseAccessToken("good")).thenReturn(claims);
        when(blacklistService.isBlacklisted("jti-1")).thenReturn(false);

        Message<?> result = interceptor.preSend(connectMessage("Bearer good"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal()).isEqualTo(claims);
    }

    @Test
    void preSendConnectWithBlacklistedTokenShouldReject() {
        when(jwtTokenProvider.parseAccessToken("revoked")).thenReturn(claims);
        when(blacklistService.isBlacklisted("jti-1")).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer revoked"), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void preSendConnectWhenRedisDownShouldFailClosed() {
        when(jwtTokenProvider.parseAccessToken("good")).thenReturn(claims);
        when(blacklistService.isBlacklisted("jti-1"))
                .thenThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer good"), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void preSendSubscribeWithoutPrincipalShouldReject() {
        UUID roomId = UUID.randomUUID();

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chat/rooms/" + roomId, false), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void preSendSubscribeToForeignRoomShouldReject() {
        UUID roomId = UUID.randomUUID();
        when(chatService.isParticipant(userId, roomId)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chat/rooms/" + roomId, true), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }

    @Test
    void preSendSubscribeToOwnRoomShouldPass() {
        UUID roomId = UUID.randomUUID();
        when(chatService.isParticipant(userId, roomId)).thenReturn(true);

        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chat/rooms/" + roomId, true), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void preSendSubscribeToNonChatDestinationShouldReject() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/other", true), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void preSendSendToParticipantRoomShouldPass() {
        UUID roomId = UUID.randomUUID();
        when(chatService.isParticipant(userId, roomId)).thenReturn(true);

        Message<?> result = interceptor.preSend(
                frame(StompCommand.SEND, "/app/chat/rooms/" + roomId, true), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void preSendSendWithMalformedRoomIdShouldReject() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/app/chat/rooms/not-a-uuid", true), channel))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }
}
