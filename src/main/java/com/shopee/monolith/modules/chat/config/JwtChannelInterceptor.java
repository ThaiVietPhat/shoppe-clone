package com.shopee.monolith.modules.chat.config;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.service.AccessTokenBlacklistService;
import com.shopee.monolith.modules.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * STOMP channel security:
 * - CONNECT requires a valid, non-blacklisted Bearer JWT (blacklist check is fail-closed).
 * - SUBSCRIBE to a room topic and SEND to a room destination require room membership.
 * Anonymous frames are rejected; HTTP handshake stays permitAll, auth lives here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/chat/rooms/";
    private static final String ROOM_SEND_PREFIX = "/app/chat/rooms/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenBlacklistService blacklistService;
    // ObjectProvider breaks the broker-config -> interceptor -> chat-service -> SimpMessagingTemplate cycle
    private final ObjectProvider<ChatService> chatServiceProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeDestination(accessor, accessor.getDestination(), ROOM_TOPIC_PREFIX);
            case SEND -> authorizeDestination(accessor, accessor.getDestination(), ROOM_SEND_PREFIX);
            default -> {
                // DISCONNECT, heartbeats, etc. pass through
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
        // Fail-closed: Redis failure surfaces as SERVICE_UNAVAILABLE and rejects the CONNECT
        if (blacklistService.isBlacklisted(claims.jti())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                claims, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))));
    }

    private void authorizeDestination(StompHeaderAccessor accessor, String destination, String roomPrefix) {
        AccessTokenClaims claims = extractClaims(accessor);
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (destination == null || !destination.startsWith(roomPrefix)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        UUID roomId = parseRoomId(destination.substring(roomPrefix.length()));
        if (!chatServiceProvider.getObject().isParticipant(claims.userId(), roomId)) {
            throw new AppException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
    }

    private AccessTokenClaims extractClaims(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof AccessTokenClaims claims) {
            return claims;
        }
        return null;
    }

    private UUID parseRoomId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
    }
}
