package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.cart.service.CartService;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.modules.order.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private CheckoutProcessor checkoutProcessor;
    @Mock
    private CartService cartService;
    @Mock
    private ProductService productService;
    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private ObjectMapper objectMapper;

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

        lenient().when(idempotencyKeyRepository.findByActorIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
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
        when(cartService.getSnapshot(buyerId)).thenReturn(new CartSnapshot(buyerId, Collections.emptyList(), 1L));

        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout(buyerId, request, idempotencyKey)
        );
        assertEquals(ErrorCode.CART_EMPTY, exception.getErrorCode());
    }

    @Test
    void checkoutWhenValidShouldCallProcessor() {
        UUID variantId = UUID.randomUUID();
        CartSnapshotItem item = new CartSnapshotItem(variantId, 2);
        when(cartService.getSnapshot(buyerId)).thenReturn(new CartSnapshot(buyerId, List.of(item), 1L));

        VariantLookupData variant = VariantLookupData.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .sku("SKU-TEST")
                .name("Test Variant")
                .price(BigDecimal.TEN)
                .build();
        ProductLookupData product = ProductLookupData.builder()
                .id(variant.productId())
                .shopId(UUID.randomUUID())
                .name("Test Product")
                .build();

        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variant));
        when(productService.findProductLookupDataById(variant.productId())).thenReturn(Optional.of(product));

        CheckoutResponse expectedResponse = CheckoutResponse.builder()
                .checkoutSessionId(UUID.randomUUID())
                .orderIds(List.of(UUID.randomUUID()))
                .status("PENDING_PAYMENT")
                .totalAmount(BigDecimal.TEN)
                .expiresAt(Instant.now())
                .build();

        when(checkoutProcessor.processCheckout(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResponse);

        CheckoutResponse response = orderService.checkout(buyerId, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(expectedResponse.checkoutSessionId(), response.checkoutSessionId());
        verify(checkoutProcessor).processCheckout(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
