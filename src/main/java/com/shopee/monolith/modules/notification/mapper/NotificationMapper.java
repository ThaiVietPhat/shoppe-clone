package com.shopee.monolith.modules.notification.mapper;

import com.shopee.monolith.modules.notification.dto.response.NotificationResponse;
import com.shopee.monolith.modules.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
