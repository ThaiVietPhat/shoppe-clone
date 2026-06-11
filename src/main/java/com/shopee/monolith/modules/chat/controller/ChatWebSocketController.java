package com.shopee.monolith.modules.chat.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.chat.dto.request.SendChatMessageRequest;
import com.shopee.monolith.modules.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.UUID;

/**
 * Realtime send path: clients SEND to /app/chat/rooms/{roomId};
 * the persisted message is broadcast to /topic/chat/rooms/{roomId}.
 * Authentication and room membership are enforced by JwtChannelInterceptor.
 */
@Controller
@RequiredArgsConstructor
@Validated
public class ChatWebSocketController {

    private final ChatService chatService;

    @MessageMapping("/chat/rooms/{roomId}")
    public void sendMessage(@DestinationVariable UUID roomId,
                            @Payload SendChatMessageRequest request,
                            Principal principal) {
        AccessTokenClaims claims = extractClaims(principal);
        chatService.sendMessage(claims.userId(), roomId, request.content());
    }

    private AccessTokenClaims extractClaims(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof AccessTokenClaims claims) {
            return claims;
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
