package com.shopee.monolith.modules.chat.service;

import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.chat.dto.response.ChatMessageResponse;
import com.shopee.monolith.modules.chat.dto.response.ChatRoomResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    /** Opens a buyer-shop room, or returns the existing one (unique per buyer+shop). */
    ChatRoomResponse openRoom(UUID buyerId, UUID shopId);

    /** Rooms the user participates in — as buyer plus as owner of their shop. */
    List<ChatRoomResponse> listRooms(UUID userId);

    /** Message history, newest first. Participants only. */
    PagedResponse<ChatMessageResponse> getMessages(UUID userId, UUID roomId, Pageable pageable);

    /** Persists a message and broadcasts it to the room's STOMP topic. Participants only. */
    ChatMessageResponse sendMessage(UUID userId, UUID roomId, String content);

    /** Read receipt: stamps the caller's last-read time on the room. Participants only. */
    ChatRoomResponse markRead(UUID userId, UUID roomId);

    /** Authorization check used by the STOMP channel interceptor. */
    boolean isParticipant(UUID userId, UUID roomId);
}
