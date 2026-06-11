package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.inventory.dto.command.ReleaseInventoryCommand;
import com.shopee.monolith.modules.inventory.service.InventoryService;
import com.shopee.monolith.modules.order.dto.response.BuyerOrderDetailResponse;
import com.shopee.monolith.modules.order.dto.response.BuyerOrderSummaryResponse;
import com.shopee.monolith.modules.order.dto.response.BuyerOrderTimelineEvent;
import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.entity.InventoryReservation;
import com.shopee.monolith.modules.order.entity.Order;
import com.shopee.monolith.modules.order.entity.OrderItem;
import com.shopee.monolith.modules.order.event.CheckoutSessionCancelledEvent;
import com.shopee.monolith.modules.order.mapper.BuyerOrderMapper;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.model.InventoryReservationStatus;
import com.shopee.monolith.modules.order.model.OrderPaymentStatus;
import com.shopee.monolith.modules.order.model.OrderStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import com.shopee.monolith.modules.order.repository.InventoryReservationRepository;
import com.shopee.monolith.modules.order.repository.OrderItemRepository;
import com.shopee.monolith.modules.order.repository.OrderRepository;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerOrderServiceImpl implements BuyerOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final InventoryService inventoryService;
    private final ShopService shopService;
    private final BuyerOrderMapper buyerOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BuyerOrderSummaryResponse> listOrders(UUID buyerId, Pageable pageable) {
        Page<Order> page = orderRepository.findAllByBuyerIdOrderByCreatedAtDesc(buyerId, pageable);
        List<Order> orders = page.getContent();

        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        Map<UUID, Long> itemCounts = orderIds.isEmpty() ? Map.of()
                : orderItemRepository.findAllByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));
        Map<UUID, ShopLookupData> shops = shopService.findShopLookupDataByIds(
                orders.stream().map(Order::getShopId).collect(Collectors.toSet()));

        List<BuyerOrderSummaryResponse> summaries = orders.stream()
                .map(order -> buyerOrderMapper.toSummaryResponse(
                        order,
                        resolveShopName(shops, order.getShopId()),
                        itemCounts.getOrDefault(order.getId(), 0L).intValue()))
                .toList();
        return PagedResponse.from(page, summaries);
    }

    @Override
    @Transactional(readOnly = true)
    public BuyerOrderDetailResponse getOrderDetail(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        Map<UUID, ShopLookupData> shops = shopService.findShopLookupDataByIds(List.of(order.getShopId()));

        return BuyerOrderDetailResponse.builder()
                .orderId(order.getId())
                .checkoutSessionId(order.getCheckoutSessionId())
                .shopId(order.getShopId())
                .shopName(resolveShopName(shops, order.getShopId()))
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .itemsSubtotal(order.getItemsSubtotal())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddressLine(order.getShippingAddressLine())
                .shippingWardName(order.getShippingWardName())
                .shippingDistrictName(order.getShippingDistrictName())
                .shippingProvinceName(order.getShippingProvinceName())
                .items(buyerOrderMapper.toItemResponses(items))
                .timeline(buildTimeline(order))
                .createdAt(order.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void cancelOrder(UUID buyerId, UUID orderId) {
        // Non-locking read: verify ownership and current state before acquiring locks
        Order orderCheck = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (orderCheck.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        UUID sessionId = orderCheck.getCheckoutSessionId();

        // Lock ordering: session → reservations (variantId ASC) → orders (id ASC)
        // Matches settlement lock order to prevent deadlock
        CheckoutSession session = checkoutSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (session.getStatus() != CheckoutSessionStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        // Cancel entire session: prevents amount mismatch on partial cancel in multi-shop checkout
        List<InventoryReservation> reservations = inventoryReservationRepository
                .findAllByCheckoutSessionIdAndStatusForUpdate(sessionId, InventoryReservationStatus.RESERVED.name());
        if (!reservations.isEmpty()) {
            List<ReleaseInventoryCommand> commands = reservations.stream()
                    .collect(Collectors.groupingBy(InventoryReservation::getVariantId,
                            Collectors.summingInt(InventoryReservation::getQuantity)))
                    .entrySet().stream()
                    .map(entry -> new ReleaseInventoryCommand(entry.getKey(), entry.getValue()))
                    .toList();
            inventoryService.release(commands);
            reservations.forEach(InventoryReservation::release);
            inventoryReservationRepository.saveAll(reservations);
        }

        List<Order> sessionOrders = orderRepository.findAllByCheckoutSessionIdForUpdate(sessionId);
        sessionOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT)
                .forEach(Order::cancel);
        orderRepository.saveAll(sessionOrders);

        session.cancel();
        checkoutSessionRepository.save(session);

        // Publish event so PaymentModule can expire non-terminal attempts after this transaction commits.
        // Deferred to AFTER_COMMIT to avoid holding session lock while touching payment_attempt rows
        // (would deadlock against webhook/timeout that locks attempt → session in opposite order).
        eventPublisher.publishEvent(new CheckoutSessionCancelledEvent(sessionId));

        log.info("Buyer {} cancelled order {} — entire session {} cancelled, released {} reservations",
                buyerId, orderId, sessionId, reservations.size());
    }

    private List<BuyerOrderTimelineEvent> buildTimeline(Order order) {
        List<BuyerOrderTimelineEvent> timeline = new ArrayList<>();
        timeline.add(BuyerOrderTimelineEvent.builder()
                .event("PLACED")
                .occurredAt(order.getCreatedAt())
                .build());
        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            timeline.add(BuyerOrderTimelineEvent.builder()
                    .event("PAID")
                    .occurredAt(order.getUpdatedAt())
                    .build());
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            timeline.add(BuyerOrderTimelineEvent.builder()
                    .event(order.getStatus().name())
                    .occurredAt(order.getUpdatedAt())
                    .build());
        }
        return timeline;
    }

    private String resolveShopName(Map<UUID, ShopLookupData> shops, UUID shopId) {
        ShopLookupData shop = shops.get(shopId);
        return shop != null ? shop.name() : null;
    }
}
