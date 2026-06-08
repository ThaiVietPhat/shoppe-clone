package com.shopee.monolith.modules.cart.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.config.CartProperties;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.cart.dto.request.AddCartItemRequest;
import com.shopee.monolith.modules.cart.dto.request.UpdateCartItemRequest;
import com.shopee.monolith.modules.cart.dto.response.CartItemResponse;
import com.shopee.monolith.modules.cart.dto.response.CartResponse;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductService productService;
    private final CartProperties cartProperties;

    private static final RedisScript<Long> CLEAR_CART_SCRIPT = RedisScript.of(
            "local currentVersion = redis.call('get', KEYS[2])\n" +
            "if currentVersion == ARGV[1] then\n" +
            "    redis.call('del', KEYS[1], KEYS[2])\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end",
            Long.class
    );

    private static final RedisScript<Long> ADD_ITEM_SCRIPT = RedisScript.of(
            "local currentQty = tonumber(redis.call('hget', KEYS[1], ARGV[1]) or '0')\n" +
            "local newQty = currentQty + tonumber(ARGV[2])\n" +
            "if newQty > tonumber(ARGV[3]) then\n" +
            "    return -1\n" +
            "end\n" +
            "redis.call('hset', KEYS[1], ARGV[1], tostring(newQty))\n" +
            "redis.call('incr', KEYS[2])\n" +
            "redis.call('expire', KEYS[1], ARGV[4])\n" +
            "redis.call('expire', KEYS[2], ARGV[4])\n" +
            "return newQty",
            Long.class
    );

    private String getItemsKey(UUID userId) {
        return "cart:" + userId + ":items";
    }

    private String getVersionKey(UUID userId) {
        return "cart:" + userId + ":version";
    }

    @Override
    public CartResponse getCart(UUID userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(itemsKey);
            String versionStr = stringRedisTemplate.opsForValue().get(versionKey);
            long version = versionStr != null ? Long.parseLong(versionStr) : 0L;

            if (entries.isEmpty()) {
                return CartResponse.builder()
                        .items(List.of())
                        .totalItems(0)
                        .version(version)
                        .build();
            }

            List<CartItemResponse> items = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                UUID variantId = UUID.fromString((String) entry.getKey());
                int quantity = Integer.parseInt((String) entry.getValue());

                var variantOpt = productService.findActiveVariantLookupDataById(variantId);
                if (variantOpt.isEmpty()) {
                    // Skip variant if it no longer exists in the system
                    continue;
                }

                VariantLookupData variant = variantOpt.get();
                UUID shopId = productService.findActiveProductLookupDataById(variant.productId())
                        .map(ProductLookupData::shopId)
                        .orElse(null);

                items.add(CartItemResponse.builder()
                        .variantId(variant.id())
                        .productId(variant.productId())
                        .shopId(shopId)
                        .name(variant.name())
                        .sku(variant.sku())
                        .price(variant.price())
                        .quantity(quantity)
                        .build());
            }

            return CartResponse.builder()
                    .items(items)
                    .totalItems(items.size())
                    .version(version)
                    .build();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        if (userId == null || request == null || request.variantId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        // Validate variant exists
        var variantOpt = productService.findActiveVariantLookupDataById(request.variantId());
        if (variantOpt.isEmpty()) {
            throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            Long result = stringRedisTemplate.execute(
                    ADD_ITEM_SCRIPT,
                    List.of(itemsKey, versionKey),
                    request.variantId().toString(),
                    String.valueOf(request.quantity()),
                    String.valueOf(cartProperties.getMaxQuantityPerItem()),
                    String.valueOf(cartProperties.getTtl().toSeconds())
            );

            if (result == null) {
                throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            if (result == -1) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        return getCart(userId);
    }

    @Override
    public CartResponse updateItem(UUID userId, UUID variantId, UpdateCartItemRequest request) {
        if (userId == null || variantId == null || request == null || request.quantity() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        int quantity = request.quantity();
        if (quantity < 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        if (quantity == 0) {
            removeItem(userId, variantId);
            return getCart(userId);
        }

        // Validate variant exists
        var variantOpt = productService.findActiveVariantLookupDataById(variantId);
        if (variantOpt.isEmpty()) {
            throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
        }

        if (quantity > cartProperties.getMaxQuantityPerItem()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            stringRedisTemplate.opsForHash().put(itemsKey, variantId.toString(), String.valueOf(quantity));
            stringRedisTemplate.opsForValue().increment(versionKey);

            // Apply sliding TTL
            stringRedisTemplate.expire(itemsKey, cartProperties.getTtl());
            stringRedisTemplate.expire(versionKey, cartProperties.getTtl());

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        return getCart(userId);
    }

    @Override
    public void removeItem(UUID userId, UUID variantId) {
        if (userId == null || variantId == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            // Delete variant from hash
            stringRedisTemplate.opsForHash().delete(itemsKey, variantId.toString());
            stringRedisTemplate.opsForValue().increment(versionKey);

            // Apply sliding TTL
            stringRedisTemplate.expire(itemsKey, cartProperties.getTtl());
            stringRedisTemplate.expire(versionKey, cartProperties.getTtl());

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void clearCart(UUID userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            stringRedisTemplate.delete(itemsKey);
            stringRedisTemplate.opsForValue().increment(versionKey);
            stringRedisTemplate.expire(versionKey, cartProperties.getTtl());
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public CartSnapshot getSnapshot(UUID userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(itemsKey);
            String versionStr = stringRedisTemplate.opsForValue().get(versionKey);
            long version = versionStr != null ? Long.parseLong(versionStr) : 0L;

            List<CartSnapshotItem> items = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                UUID variantId = UUID.fromString((String) entry.getKey());
                int quantity = Integer.parseInt((String) entry.getValue());
                items.add(new CartSnapshotItem(variantId, quantity));
            }

            return CartSnapshot.builder()
                    .userId(userId)
                    .items(items)
                    .version(version)
                    .build();

        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void clearSnapshotIfVersionUnchanged(UUID userId, long version) {
        if (userId == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        try {
            String itemsKey = getItemsKey(userId);
            String versionKey = getVersionKey(userId);

            stringRedisTemplate.execute(
                    CLEAR_CART_SCRIPT,
                    List.of(itemsKey, versionKey),
                    String.valueOf(version)
            );
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
