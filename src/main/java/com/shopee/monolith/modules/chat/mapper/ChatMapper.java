package com.shopee.monolith.modules.chat.mapper;

import com.shopee.monolith.modules.chat.dto.response.ChatMessageResponse;
import com.shopee.monolith.modules.chat.dto.response.ChatRoomResponse;
import com.shopee.monolith.modules.chat.entity.ChatMessage;
import com.shopee.monolith.modules.chat.entity.ChatRoom;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    @Mapping(target = "id", source = "room.id")
    @Mapping(target = "createdAt", source = "room.createdAt")
    ChatRoomResponse toRoomResponse(ChatRoom room, String shopName);

    ChatMessageResponse toMessageResponse(ChatMessage message);
}
