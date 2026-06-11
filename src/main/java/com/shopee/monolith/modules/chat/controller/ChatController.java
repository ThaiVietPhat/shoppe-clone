package com.shopee.monolith.modules.chat.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.chat.dto.request.OpenChatRoomRequest;
import com.shopee.monolith.modules.chat.dto.request.SendChatMessageRequest;
import com.shopee.monolith.modules.chat.dto.response.ChatMessageResponse;
import com.shopee.monolith.modules.chat.dto.response.ChatRoomResponse;
import com.shopee.monolith.modules.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Buyer-seller chat: REST room/message history; realtime delivery via STOMP /ws")
public class ChatController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatService chatService;

    @Operation(
            summary = "Open (or reuse) a chat room with a shop",
            description = "One room per buyer+shop pair. Returns the existing room if already opened. "
                    + "A seller cannot open a room with their own shop.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Room returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Cannot chat with own shop.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Shop not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PostMapping("/rooms")
    public ApiResponse<ChatRoomResponse> openRoom(
            @Valid @RequestBody OpenChatRoomRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(chatService.openRoom(claims.userId(), request.shopId()));
    }

    @Operation(
            summary = "List my chat rooms",
            description = "Rooms the caller participates in — as buyer, plus rooms of their own shop when seller.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Rooms returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> listRooms(@AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(chatService.listRooms(claims.userId()));
    }

    @Operation(
            summary = "Get room message history",
            description = "Paged message history, newest first. Participants only. "
                    + "Realtime messages are also broadcast to STOMP topic /topic/chat/rooms/{roomId}.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Messages returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Caller is not a participant of this room.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Room not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<PagedResponse<ChatMessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.success(
                chatService.getMessages(claims.userId(), roomId, PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @Operation(
            summary = "Send a message (REST path)",
            description = "Persists the message and broadcasts it to the room's STOMP topic. Participants only.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Message sent.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Caller is not a participant of this room.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Room not found.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PostMapping("/rooms/{roomId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(chatService.sendMessage(claims.userId(), roomId, request.content()));
    }

    @Operation(
            summary = "Mark room read (read receipt)",
            description = "Stamps the caller's last-read time on the room. Participants only.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Read receipt updated.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Caller is not a participant of this room.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PostMapping("/rooms/{roomId}/read")
    public ApiResponse<ChatRoomResponse> markRead(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(chatService.markRead(claims.userId(), roomId));
    }

    private void requireAuthenticated(AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
