package com.shopee.monolith.modules.chat.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "buyer_last_read_at")
    private Instant buyerLastReadAt;

    @Column(name = "seller_last_read_at")
    private Instant sellerLastReadAt;

    public void markBuyerRead(Instant now) {
        this.buyerLastReadAt = now;
    }

    public void markSellerRead(Instant now) {
        this.sellerLastReadAt = now;
    }
}
