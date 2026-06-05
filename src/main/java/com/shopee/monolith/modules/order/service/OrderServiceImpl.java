package com.shopee.monolith.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.cart.service.CartService;
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
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CheckoutResponse checkout(UUID buyerId, CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }

        String requestHash = computeRequestHash(request);
        UUID keyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(1));

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

        // Proceed with checkout
        CartSnapshot cartSnapshot = cartService.getSnapshot(buyerId);
        if (cartSnapshot == null || cartSnapshot.items().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderCreationInfo> orderCreationInfos = new ArrayList<>();
        List<ReserveInventoryCommand> reserveCommands = new ArrayList<>();

        // Resolve variants and group by shopId
        List<CartItemWithDetails> itemsWithDetails = new ArrayList<>();
        for (CartSnapshotItem item : cartSnapshot.items()) {
            VariantLookupData variant = productService.findVariantLookupDataById(item.variantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

            ProductLookupData product = productService.findProductLookupDataById(variant.productId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

            itemsWithDetails.add(new CartItemWithDetails(item, variant, product));
            reserveCommands.add(new ReserveInventoryCommand(item.variantId(), item.quantity()));
        }

        Map<UUID, List<CartItemWithDetails>> itemsByShop = itemsWithDetails.stream()
                .collect(Collectors.groupingBy(item -> item.product.shopId()));

        CheckoutSession session = CheckoutSession.builder()
                .buyerId(buyerId)
                .status(CheckoutSessionStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.ZERO) // temporary
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
        session = checkoutSessionRepository.save(session);

        List<UUID> orderIds = new ArrayList<>();

        for (Map.Entry<UUID, List<CartItemWithDetails>> entry : itemsByShop.entrySet()) {
            UUID shopId = entry.getKey();
            List<CartItemWithDetails> shopItems = entry.getValue();

            BigDecimal shopTotal = shopItems.stream()
                    .map(item -> item.variant.price().multiply(BigDecimal.valueOf(item.cartItem.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalAmount = totalAmount.add(shopTotal);

            Order order = Order.builder()
                    .buyerId(buyerId)
                    .shopId(shopId)
                    .checkoutSessionId(session.getId())
                    .status(OrderStatus.PENDING_PAYMENT)
                    .totalAmount(shopTotal)
                    .build();
            order = orderRepository.save(order);
            orderIds.add(order.getId());

            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItemWithDetails detail : shopItems) {
                BigDecimal subtotal = detail.variant.price().multiply(BigDecimal.valueOf(detail.cartItem.quantity()));
                OrderItem orderItem = OrderItem.builder()
                        .orderId(order.getId())
                        .variantId(detail.cartItem.variantId())
                        .productName(detail.product.name())
                        .variantName(detail.variant.name())
                        .sku(detail.variant.sku())
                        .price(detail.variant.price())
                        .quantity(detail.cartItem.quantity())
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
                .expiresAt(session.getExpiresAt())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
        checkoutSessionRepository.save(sessionToUpdate);

        // Reserve inventory
        inventoryService.reserve(reserveCommands);

        // Save reservations
        for (OrderCreationInfo oInfo : orderCreationInfos) {
            List<InventoryReservation> reservations = new ArrayList<>();
            for (OrderItem item : oInfo.items) {
                reservations.add(InventoryReservation.builder()
                        .checkoutSessionId(session.getId())
                        .orderId(oInfo.order.getId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .status(InventoryReservationStatus.RESERVED)
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

        // Register synchronization to clear cart after commit
        long cartVersion = cartSnapshot.version();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        cartService.clearSnapshotIfVersionUnchanged(buyerId, cartVersion);
                    } catch (Exception e) {
                        log.error("Failed to clear cart snapshot post-commit", e);
                    }
                }
            });
        }

        return response;
    }

    private String computeRequestHash(CheckoutRequest request) {
        try {
            String canonical = (request.shippingStreet() != null ? request.shippingStreet().trim() : "")
                    + "|" + (request.shippingCity() != null ? request.shippingCity().trim() : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute request hash", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private record CartItemWithDetails(
            CartSnapshotItem cartItem,
            VariantLookupData variant,
            ProductLookupData product
    ) {}

    private record OrderCreationInfo(
            Order order,
            List<OrderItem> items
    ) {}
}
