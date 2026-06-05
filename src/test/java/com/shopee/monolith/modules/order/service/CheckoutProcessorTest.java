package com.shopee.monolith.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.inventory.service.InventoryService;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;
import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.entity.IdempotencyKey;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.model.IdempotencyStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import com.shopee.monolith.modules.order.repository.IdempotencyKeyRepository;
import com.shopee.monolith.modules.order.repository.InventoryReservationRepository;
import com.shopee.monolith.modules.order.repository.OrderItemRepository;
import com.shopee.monolith.modules.order.repository.OrderRepository;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutProcessorTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InventoryReservationRepository inventoryReservationRepository;
    @Mock
    private InventoryService inventoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @InjectMocks
    private CheckoutProcessor checkoutProcessor;

    private UUID buyerId;
    private CheckoutRequest request;
    private String idempotencyKey;
    private String requestHash;
    private UUID keyId;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        request = new CheckoutRequest("123 Street", "Hanoi");
        idempotencyKey = UUID.randomUUID().toString();
        requestHash = "hash123";
        keyId = UUID.randomUUID();
        expiresAt = Instant.now();
    }

    @Test
    void processCheckoutWhenDuplicateSameRequestAndCompletedShouldReturnCachedResponse() throws Exception {
        CheckoutResponse expectedResponse = CheckoutResponse.builder()
                .checkoutSessionId(UUID.randomUUID())
                .orderIds(List.of(UUID.randomUUID()))
                .status("PENDING_PAYMENT")
                .totalAmount(BigDecimal.TEN)
                .expiresAt(Instant.now())
                .build();

        String responseBody = objectMapper.writeValueAsString(expectedResponse);

        IdempotencyKey completedKey = IdempotencyKey.builder()
                .actorId(buyerId)
                .operation("CHECKOUT")
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(IdempotencyStatus.COMPLETED)
                .responseBody(responseBody)
                .build();

        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(idempotencyKeyRepository.findByKeysForUpdate(buyerId, "CHECKOUT", idempotencyKey))
                .thenReturn(Optional.of(completedKey));

        CheckoutResponse actualResponse = checkoutProcessor.processCheckout(
                buyerId, request, idempotencyKey, requestHash, keyId, expiresAt, List.of(), () -> {}
        );

        assertNotNull(actualResponse);
        assertEquals(expectedResponse.checkoutSessionId(), actualResponse.checkoutSessionId());
        assertEquals(expectedResponse.totalAmount(), actualResponse.totalAmount());
    }

    @Test
    void processCheckoutWhenDuplicateDifferentRequestShouldThrowConflict() {
        IdempotencyKey existingKey = IdempotencyKey.builder()
                .actorId(buyerId)
                .operation("CHECKOUT")
                .idempotencyKey(idempotencyKey)
                .requestHash("different_hash")
                .status(IdempotencyStatus.COMPLETED)
                .build();

        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(idempotencyKeyRepository.findByKeysForUpdate(buyerId, "CHECKOUT", idempotencyKey))
                .thenReturn(Optional.of(existingKey));

        AppException exception = assertThrows(AppException.class, () ->
                checkoutProcessor.processCheckout(buyerId, request, idempotencyKey, requestHash, keyId, expiresAt, List.of(), () -> {})
        );
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void processCheckoutWhenDuplicateSameRequestAndProcessingShouldThrowRetryableConflict() {
        IdempotencyKey existingKey = IdempotencyKey.builder()
                .actorId(buyerId)
                .operation("CHECKOUT")
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(IdempotencyStatus.PROCESSING)
                .build();

        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(idempotencyKeyRepository.findByKeysForUpdate(buyerId, "CHECKOUT", idempotencyKey))
                .thenReturn(Optional.of(existingKey));

        AppException exception = assertThrows(AppException.class, () ->
                checkoutProcessor.processCheckout(buyerId, request, idempotencyKey, requestHash, keyId, expiresAt, List.of(), () -> {})
        );
        assertEquals(ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING, exception.getErrorCode());
    }

    @Test
    void processCheckoutWhenValidShouldCreateEntities() {
        UUID variantId = UUID.randomUUID();
        CartSnapshotItem cartItem = new CartSnapshotItem(variantId, 2);
        VariantLookupData variant = VariantLookupData.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .sku("SKU-1")
                .name("Var 1")
                .price(BigDecimal.TEN)
                .build();
        ProductLookupData product = ProductLookupData.builder()
                .id(variant.productId())
                .shopId(UUID.randomUUID())
                .name("Prod 1")
                .build();

        OrderServiceImpl.CartItemWithDetails item = new OrderServiceImpl.CartItemWithDetails(cartItem, variant, product);

        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        CheckoutSession session = CheckoutSession.builder()
                .id(UUID.randomUUID())
                .buyerId(buyerId)
                .status(CheckoutSessionStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.TEN)
                .shippingStreet(request.shippingStreet())
                .shippingCity(request.shippingCity())
                .expiresAt(Instant.now())
                .build();
        when(checkoutSessionRepository.save(any())).thenReturn(session);
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResponse response = checkoutProcessor.processCheckout(
                buyerId, request, idempotencyKey, requestHash, keyId, expiresAt, List.of(item), () -> {}
        );

        assertNotNull(response);
        assertEquals(session.getId(), response.checkoutSessionId());
        verify(inventoryService).reserve(any());
        verify(inventoryReservationRepository).saveAll(any());
    }
}
