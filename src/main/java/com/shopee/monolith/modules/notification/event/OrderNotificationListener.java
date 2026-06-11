package com.shopee.monolith.modules.notification.event;

import com.shopee.monolith.modules.notification.model.NotificationType;
import com.shopee.monolith.modules.notification.service.NotificationInboxService;
import com.shopee.monolith.modules.order.event.OrderConfirmedEvent;
import com.shopee.monolith.modules.order.event.OrderFulfillmentChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Writes buyer inbox notifications for order lifecycle events.
 * Runs AFTER_COMMIT so a notification failure never rolls back the order transition.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationListener {

    private static final String REF_TYPE_ORDER = "ORDER";

    private final NotificationInboxService inboxService;

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        for (UUID orderId : event.orderIds()) {
            inboxService.createNotification(
                    event.buyerId(),
                    NotificationType.ORDER_CONFIRMED,
                    "Your order has been confirmed",
                    "Payment via " + event.paymentMethod() + " succeeded. The seller is preparing your order.",
                    REF_TYPE_ORDER,
                    orderId);
        }
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFulfillmentChanged(OrderFulfillmentChangedEvent event) {
        switch (event.fulfillmentStatus()) {
            case "SHIPPED" -> inboxService.createNotification(
                    event.buyerId(),
                    NotificationType.ORDER_SHIPPED,
                    "Your order has been shipped",
                    "The seller handed your order to the carrier.",
                    REF_TYPE_ORDER,
                    event.orderId());
            case "DELIVERED" -> {
                inboxService.createNotification(
                        event.buyerId(),
                        NotificationType.ORDER_DELIVERED,
                        "Your order has been delivered",
                        "Enjoy your purchase!",
                        REF_TYPE_ORDER,
                        event.orderId());
                inboxService.createNotification(
                        event.buyerId(),
                        NotificationType.REVIEW_REMINDER,
                        "How was your order?",
                        "Leave a review for the items you received.",
                        REF_TYPE_ORDER,
                        event.orderId());
            }
            default -> log.debug("No notification for fulfillment status {}", event.fulfillmentStatus());
        }
    }
}
