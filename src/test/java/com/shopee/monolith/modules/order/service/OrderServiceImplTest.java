package com.shopee.monolith.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.cart.service.CartService;
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
import com.shopee.monolith.modules.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

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
    private CartService cartService;
    @Mock
    private ProductService productService;
    @Mock
    private InventoryService inventoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID buyerId;
    private CheckoutRequest request;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        request = new CheckoutRequest("123 Street", "Hanoi");
        idempotencyKey = UUID.randomUUID().toString();
    }

    @Test
    void checkoutWhenMissingIdempotencyKeyShouldThrowException() {
        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout(buyerId, request, "")
        );
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_MISSING, exception.getErrorCode());
    }

    @Test
    void checkoutWhenCartIsEmptyShouldThrowException() {
        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(cartService.getSnapshot(buyerId)).thenReturn(new CartSnapshot(buyerId, Collections.emptyList(), 1L));

        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout(buyerId, request, idempotencyKey)
        );
        assertEquals(ErrorCode.CART_EMPTY, exception.getErrorCode());
    }

    @Test
    void checkoutWhenDuplicateSameRequestAndCompletedShouldReturnCachedResponse() throws Exception {
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
                .requestHash(computeRequestHash(request))
                .status(IdempotencyStatus.COMPLETED)
                .responseBody(responseBody)
                .build();

        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(idempotencyKeyRepository.findByKeysForUpdate(buyerId, "CHECKOUT", idempotencyKey))
                .thenReturn(Optional.of(completedKey));

        CheckoutResponse actualResponse = orderService.checkout(buyerId, request, idempotencyKey);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse.checkoutSessionId(), actualResponse.checkoutSessionId());
        assertEquals(expectedResponse.totalAmount(), actualResponse.totalAmount());
    }

    @Test
    void checkoutWhenDuplicateDifferentRequestShouldThrowConflict() {
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
                orderService.checkout(buyerId, request, idempotencyKey)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void checkoutWhenDuplicateSameRequestAndProcessingShouldThrowRetryableConflict() {
        IdempotencyKey existingKey = IdempotencyKey.builder()
                .actorId(buyerId)
                .operation("CHECKOUT")
                .idempotencyKey(idempotencyKey)
                .requestHash(computeRequestHash(request))
                .status(IdempotencyStatus.PROCESSING)
                .build();

        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(idempotencyKeyRepository.findByKeysForUpdate(buyerId, "CHECKOUT", idempotencyKey))
                .thenReturn(Optional.of(existingKey));

        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout(buyerId, request, idempotencyKey)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING, exception.getErrorCode());
    }

    @Test
    void checkoutWhenInsufficientStockShouldPropagateException() {
        UUID variantId = UUID.randomUUID();
        CartSnapshotItem item = new CartSnapshotItem(variantId, 5);
        when(idempotencyKeyRepository.tryInsert(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(cartService.getSnapshot(buyerId)).thenReturn(new CartSnapshot(buyerId, List.of(item), 1L));

        VariantLookupData variant = VariantLookupData.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .sku("SKU1")
                .name("Variant 1")
                .price(BigDecimal.TEN)
                .build();
        ProductLookupData product = ProductLookupData.builder()
                .id(variant.productId())
                .shopId(UUID.randomUUID())
                .name("Product 1")
                .build();

        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variant));
        when(productService.findProductLookupDataById(variant.productId())).thenReturn(Optional.of(product));

        CheckoutSession session = CheckoutSession.builder()
                .id(UUID.randomUUID())
                .buyerId(buyerId)
                .status(CheckoutSessionStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.TEN)
                .expiresAt(Instant.now())
                .build();
        when(checkoutSessionRepository.save(any())).thenReturn(session);
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new AppException(ErrorCode.INSUFFICIENT_STOCK)).when(inventoryService).reserve(any());

        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout(buyerId, request, idempotencyKey)
        );
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    private String computeRequestHash(CheckoutRequest req) {
        try {
            String canonical = (req.shippingStreet() != null ? req.shippingStreet().trim() : "")
                    + "|" + (req.shippingCity() != null ? req.shippingCity().trim() : "");
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            throw new RuntimeException(e);
        }
    }
}
