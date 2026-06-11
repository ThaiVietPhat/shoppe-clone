package com.shopee.monolith.modules.chat.repository;

import com.shopee.monolith.modules.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByBuyerIdAndShopId(UUID buyerId, UUID shopId);

    List<ChatRoom> findAllByBuyerIdOrderByUpdatedAtDesc(UUID buyerId);

    List<ChatRoom> findAllByShopIdOrderByUpdatedAtDesc(UUID shopId);
}
