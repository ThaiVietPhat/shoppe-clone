package com.shopee.monolith.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.inventory.dto.command.ReserveInventoryCommand;
import com.shopee.monolith.modules.inventory.service.InventoryService;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;
import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.entity.IdempotencyKey;
import com.shopee.monolith.modules.order.entity.InventoryReservation;
import com.shopee.monolith.modules.order.entity.Order;
import com.shopee.monolith.modules.order.entity.OrderItem;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.model.IdempotencyStatus;
import com.shopee.monolith.modules.order.model.InventoryReservationStatus;
import com.shopee.monolith.modules.order.model.OrderStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import com.shopee.monolith.modules.order.repository.IdempotencyKeyRepository;
import com.shopee.monolith.modules.order.repository.InventoryReservationRepository;
import com.shopee.monolith.modules.order.repository.OrderItemRepository;
import com.shopee.monolith.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutProcessor {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CheckoutResponse processCheckout(UUID buyerId, CheckoutRequest request, String idempotencyKey,
                                            String requestHash, UUID keyId, Instant expiresAt,
                                            List<OrderServiceImpl.CartItemWithDetails> resolvedItems,
                                            Runnable postCommitAction) {

        int inserted = idempotencyKeyRepository.tryInsert(
                keyId,
                buyerId,
                "CHECKOUT",
                idempotencyKey,
                requestHash,
                IdempotencyStatus.PROCESSING.name(),
                expiresAt
        );

        if (inserted == 0) {
            // Duplicate key: lock and inspect
            IdempotencyKey existing = idempotencyKeyRepository.findByKeysForUpdate(buyerId, "CHECKOUT", idempotencyKey)
                    .orElseThrow(() -> new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }

            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new AppException(ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING);
            }

            // COMPLETED: deserialize cached response
            try {
                return objectMapper.readValue(existing.getResponseBody(), CheckoutResponse.class);
            } catch (Exception e) {
                log.error("Failed to deserialize cached checkout response", e);
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderCreationInfo> orderCreationInfos = new ArrayList<>();
        List<ReserveInventoryCommand> reserveCommands = new ArrayList<>();

        for (OrderServiceImpl.CartItemWithDetails item : resolvedItems) {
            reserveCommands.add(new ReserveInventoryCommand(item.cartItem().variantId(), item.cartItem().quantity()));
        }

        Map<UUID, List<OrderServiceImpl.CartItemWithDetails>> itemsByShop = resolvedItems.stream()
                .collect(Collectors.groupingBy(item -> item.product().shopId()));

        CheckoutSession session = CheckoutSession.builder()
                .buyerId(buyerId)
                .status(CheckoutSessionStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.ZERO) // temporary
                .shippingStreet(request.shippingStreet())
                .shippingCity(request.shippingCity())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
        session = checkoutSessionRepository.save(session);

        List<UUID> orderIds = new ArrayList<>();

        for (Map.Entry<UUID, List<OrderServiceImpl.CartItemWithDetails>> entry : itemsByShop.entrySet()) {
            UUID shopId = entry.getKey();
            List<OrderServiceImpl.CartItemWithDetails> shopItems = entry.getValue();

            BigDecimal shopTotal = shopItems.stream()
                    .map(item -> item.variant().price().multiply(BigDecimal.valueOf(item.cartItem().quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalAmount = totalAmount.add(shopTotal);

            Order order = Order.builder()
                    .buyerId(buyerId)
                    .shopId(shopId)
                    .checkoutSessionId(session.getId())
                    .status(OrderStatus.PENDING_PAYMENT)
                    .totalAmount(shopTotal)
                    .shippingStreet(request.shippingStreet())
                    .shippingCity(request.shippingCity())
                    .build();
            order = orderRepository.save(order);
            orderIds.add(order.getId());

            List<OrderItem> orderItems = new ArrayList<>();
            for (OrderServiceImpl.CartItemWithDetails detail : shopItems) {
                BigDecimal subtotal = detail.variant().price().multiply(BigDecimal.valueOf(detail.cartItem().quantity()));
                OrderItem orderItem = OrderItem.builder()
                        .orderId(order.getId())
                        .variantId(detail.cartItem().variantId())
                        .productName(detail.product().name())
                        .variantName(detail.variant().name())
                        .sku(detail.variant().sku())
                        .price(detail.variant().price())
                        .quantity(detail.cartItem().quantity())
                        .subtotal(subtotal)
                        .build();
                orderItems.add(orderItem);
            }
            orderItemRepository.saveAll(orderItems);
            orderCreationInfos.add(new OrderCreationInfo(order, orderItems));
        }

        // Update total amount on checkout session
        CheckoutSession sessionToUpdate = CheckoutSession.builder()
                .id(session.getId())
                .buyerId(session.getBuyerId())
                .status(session.getStatus())
                .totalAmount(totalAmount)
                .shippingStreet(session.getShippingStreet())
                .shippingCity(session.getShippingCity())
                .expiresAt(session.getExpiresAt())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
        checkoutSessionRepository.save(sessionToUpdate);

        // Reserve inventory (this internally does SELECT FOR UPDATE on inventories table)
        inventoryService.reserve(reserveCommands);

        // Save reservations ledger
        for (OrderCreationInfo oInfo : orderCreationInfos) {
            List<InventoryReservation> reservations = new ArrayList<>();
            for (OrderItem item : oInfo.items) {
                reservations.add(InventoryReservation.builder()
                        .checkoutSessionId(session.getId())
                        .orderId(oInfo.order.getId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .status(InventoryReservationStatus.RESERVED)
                        .expiresAt(session.getExpiresAt())
                        .build());
            }
            inventoryReservationRepository.saveAll(reservations);
        }

        CheckoutResponse response = CheckoutResponse.builder()
                .checkoutSessionId(session.getId())
                .orderIds(orderIds)
                .status(CheckoutSessionStatus.PENDING_PAYMENT.name())
                .totalAmount(totalAmount)
                .expiresAt(session.getExpiresAt())
                .build();

        // Save Response and complete IdempotencyKey
        try {
            String responseBody = objectMapper.writeValueAsString(response);
            IdempotencyKey completedKey = IdempotencyKey.builder()
                    .id(keyId)
                    .actorId(buyerId)
                    .operation("CHECKOUT")
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .status(IdempotencyStatus.COMPLETED)
                    .responseBody(responseBody)
                    .expiresAt(expiresAt)
                    .build();
            idempotencyKeyRepository.save(completedKey);
        } catch (Exception e) {
            log.error("Failed to serialize checkout response for idempotency caching", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        postCommitAction.run();
                    } catch (Exception e) {
                        log.error("Failed to execute post-commit action", e);
                    }
                }
            });
        }

        return response;
    }

    private record OrderCreationInfo(
            Order order,
            List<OrderItem> items
    ) {}
}
