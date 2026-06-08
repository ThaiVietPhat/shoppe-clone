package com.shopee.monolith.modules.cart.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.config.CartProperties;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.request.AddCartItemRequest;
import com.shopee.monolith.modules.cart.dto.request.UpdateCartItemRequest;
import com.shopee.monolith.modules.cart.dto.response.CartResponse;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ProductService productService;

    @Mock
    private CartProperties cartProperties;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CartServiceImpl cartService;

    private final UUID userId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID shopId = UUID.randomUUID();

    private VariantLookupData variantLookup;
    private ProductLookupData productLookup;

    @BeforeEach
    void setUp() {
        variantLookup = VariantLookupData.builder()
                .id(variantId)
                .productId(productId)
                .sku("SKU-VARIANT-1")
                .name("Test Variant")
                .price(new BigDecimal("150.00"))
                .build();

        productLookup = ProductLookupData.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(UUID.randomUUID())
                .name("Test Product")
                .build();
    }

    @Test
    void getCartWhenEmptyShouldReturnEmptyResponse() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        when(hashOperations.entries("cart:" + userId + ":items")).thenReturn(Collections.emptyMap());
        when(valueOperations.get("cart:" + userId + ":version")).thenReturn(null);

        CartResponse response = cartService.getCart(userId);

        assertNotNull(response);
        assertEquals(0, response.items().size());
        assertEquals(0, response.totalItems());
        assertEquals(0L, response.version());
    }

    @Test
    void getCartWhenItemsExistShouldReturnPopulatedResponse() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<Object, Object> hashEntries = new HashMap<>();
        hashEntries.put(variantId.toString(), "3");
        when(hashOperations.entries("cart:" + userId + ":items")).thenReturn(hashEntries);
        when(valueOperations.get("cart:" + userId + ":version")).thenReturn("4");

        when(productService.findActiveVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(productService.findActiveProductLookupDataById(productId)).thenReturn(Optional.of(productLookup));

        CartResponse response = cartService.getCart(userId);

        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals(1, response.totalItems());
        assertEquals(4L, response.version());

        var item = response.items().get(0);
        assertEquals(variantId, item.variantId());
        assertEquals(productId, item.productId());
        assertEquals(shopId, item.shopId());
        assertEquals("Test Variant", item.name());
        assertEquals(3, item.quantity());
    }

    @Test
    void addItemSuccessShouldAddAndIncrementVersion() {
        AddCartItemRequest request = new AddCartItemRequest(variantId, 2);

        when(productService.findActiveVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cartProperties.getMaxQuantityPerItem()).thenReturn(99);
        when(cartProperties.getTtl()).thenReturn(Duration.ofDays(7));

        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("cart:" + userId + ":items", "cart:" + userId + ":version")),
                eq(variantId.toString()),
                eq("2"),
                eq("99"),
                eq("604800")
        )).thenReturn(5L);

        // For getCart call at the end
        Map<Object, Object> hashEntries = new HashMap<>();
        hashEntries.put(variantId.toString(), "5");
        when(hashOperations.entries("cart:" + userId + ":items")).thenReturn(hashEntries);
        when(valueOperations.get("cart:" + userId + ":version")).thenReturn("6");
        when(productService.findActiveProductLookupDataById(productId)).thenReturn(Optional.of(productLookup));

        CartResponse response = cartService.addItem(userId, request);

        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("cart:" + userId + ":items", "cart:" + userId + ":version")),
                eq(variantId.toString()),
                eq("2"),
                eq("99"),
                eq("604800")
        );

        assertNotNull(response);
        assertEquals(5, response.items().get(0).quantity());
    }

    @Test
    void addItemWhenInvalidVariantShouldThrowNotFound() {
        AddCartItemRequest request = new AddCartItemRequest(variantId, 2);
        when(productService.findActiveVariantLookupDataById(variantId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> cartService.addItem(userId, request));
        assertEquals(ErrorCode.VARIANT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void addItemWhenExceedsMaxQuantityShouldThrowInvalidRequest() {
        AddCartItemRequest request = new AddCartItemRequest(variantId, 90);

        when(productService.findActiveVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(cartProperties.getMaxQuantityPerItem()).thenReturn(99);
        when(cartProperties.getTtl()).thenReturn(Duration.ofDays(7));

        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("cart:" + userId + ":items", "cart:" + userId + ":version")),
                eq(variantId.toString()),
                eq("90"),
                eq("99"),
                eq("604800")
        )).thenReturn(-1L);

        AppException exception = assertThrows(AppException.class, () -> cartService.addItem(userId, request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void updateItemWhenQuantityZeroShouldRemoveItem() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(0);

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cartProperties.getTtl()).thenReturn(Duration.ofDays(7));

        // Mock empty cart on subsequent getCart inside updateItem
        when(hashOperations.entries("cart:" + userId + ":items")).thenReturn(Collections.emptyMap());
        when(valueOperations.get("cart:" + userId + ":version")).thenReturn("2");

        CartResponse response = cartService.updateItem(userId, variantId, request);

        verify(hashOperations).delete("cart:" + userId + ":items", variantId.toString());
        verify(valueOperations).increment("cart:" + userId + ":version");
        assertNotNull(response);
        assertEquals(0, response.items().size());
    }

    @Test
    void updateItemWhenNegativeQuantityShouldThrowInvalidRequest() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(-1);

        AppException exception = assertThrows(AppException.class, () -> cartService.updateItem(userId, variantId, request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void removeItemShouldCallRedisAndDelete() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cartProperties.getTtl()).thenReturn(Duration.ofDays(7));

        cartService.removeItem(userId, variantId);

        verify(hashOperations).delete("cart:" + userId + ":items", variantId.toString());
        verify(valueOperations).increment("cart:" + userId + ":version");
        verify(stringRedisTemplate).expire("cart:" + userId + ":items", Duration.ofDays(7));
    }

    @Test
    void clearCartShouldDeleteKeys() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cartProperties.getTtl()).thenReturn(Duration.ofDays(7));

        cartService.clearCart(userId);

        verify(stringRedisTemplate).delete("cart:" + userId + ":items");
        verify(valueOperations).increment("cart:" + userId + ":version");
        verify(stringRedisTemplate).expire("cart:" + userId + ":version", Duration.ofDays(7));
    }

    @Test
    void getSnapshotSuccessShouldReturnSnapshot() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<Object, Object> hashEntries = new HashMap<>();
        hashEntries.put(variantId.toString(), "4");
        when(hashOperations.entries("cart:" + userId + ":items")).thenReturn(hashEntries);
        when(valueOperations.get("cart:" + userId + ":version")).thenReturn("10");

        CartSnapshot snapshot = cartService.getSnapshot(userId);

        assertNotNull(snapshot);
        assertEquals(userId, snapshot.userId());
        assertEquals(10L, snapshot.version());
        assertEquals(1, snapshot.items().size());
        assertEquals(variantId, snapshot.items().get(0).variantId());
        assertEquals(4, snapshot.items().get(0).quantity());
    }

    @Test
    void clearSnapshotIfVersionUnchangedShouldExecuteLuaScript() {
        cartService.clearSnapshotIfVersionUnchanged(userId, 10L);

        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("cart:" + userId + ":items", "cart:" + userId + ":version")),
                eq("10")
        );
    }

    @Test
    void redisFailureShouldMapToServiceUnavailable() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        doThrow(new RedisSystemException("Redis Connection Refused", new RuntimeException()))
                .when(hashOperations).entries(anyString());

        AppException exception = assertThrows(AppException.class, () -> cartService.getCart(userId));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
}
