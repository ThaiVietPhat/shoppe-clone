package com.shopee.monolith.modules.notification.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import com.shopee.monolith.modules.notification.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", length = 2000)
    private String body;

    @Column(name = "ref_type", length = 40)
    private String refType;

    @Column(name = "ref_id")
    private UUID refId;

    @Column(name = "read_at")
    private Instant readAt;

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
