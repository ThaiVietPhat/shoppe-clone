package com.shopee.monolith.modules.notification.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.notification.dto.response.NotificationResponse;
import com.shopee.monolith.modules.notification.entity.Notification;
import com.shopee.monolith.modules.notification.mapper.NotificationMapper;
import com.shopee.monolith.modules.notification.model.NotificationType;
import com.shopee.monolith.modules.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-06-12T10:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;

    private NotificationInboxServiceImpl service;

    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new NotificationInboxServiceImpl(
                notificationRepository, notificationMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    @Test
    void markReadWhenOwnedShouldStampReadAt() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .userId(userId).type(NotificationType.ORDER_CONFIRMED).title("t").build();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(mock(NotificationResponse.class));

        service.markRead(userId, notificationId);

        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void markReadWhenAlreadyReadShouldKeepOriginalTimestamp() {
        Instant earlier = NOW.minusSeconds(3600);
        Notification notification = Notification.builder()
                .userId(userId).type(NotificationType.ORDER_CONFIRMED).title("t").readAt(earlier).build();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(mock(NotificationResponse.class));

        service.markRead(userId, notificationId);

        assertThat(notification.getReadAt()).isEqualTo(earlier);
    }

    @Test
    void markReadWhenNotOwnerShouldThrowNotFound() {
        UUID notificationId = UUID.randomUUID();
        Notification foreign = Notification.builder()
                .userId(UUID.randomUUID()).type(NotificationType.ORDER_CONFIRMED).title("t").build();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.markRead(userId, notificationId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAllReadShouldDelegateToRepositoryWithClockInstant() {
        when(notificationRepository.markAllRead(userId, NOW)).thenReturn(4);

        assertThat(service.markAllRead(userId)).isEqualTo(4);
    }

    @Test
    void countUnreadShouldDelegateToRepository() {
        when(notificationRepository.countByUserIdAndReadAtIsNull(userId)).thenReturn(7L);

        assertThat(service.countUnread(userId)).isEqualTo(7);
    }

    @Test
    void createNotificationShouldPersistAllFields() {
        UUID refId = UUID.randomUUID();

        service.createNotification(userId, NotificationType.ORDER_SHIPPED, "title", "body", "ORDER", refId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_SHIPPED);
        assertThat(saved.getTitle()).isEqualTo("title");
        assertThat(saved.getRefId()).isEqualTo(refId);
        assertThat(saved.isRead()).isFalse();
    }
}
