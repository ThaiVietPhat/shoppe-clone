package com.shopee.monolith.modules.cart.service;

import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.request.AddCartItemRequest;
import com.shopee.monolith.modules.cart.dto.request.UpdateCartItemRequest;
import com.shopee.monolith.modules.cart.dto.response.CartResponse;

import java.util.List;
import java.util.UUID;

public interface CartService {
    CartResponse getCart(UUID userId);
    CartResponse addItem(UUID userId, AddCartItemRequest request);
    CartResponse updateItem(UUID userId, UUID variantId, UpdateCartItemRequest request);
    void removeItem(UUID userId, UUID variantId);
    void clearCart(UUID userId);
    CartResponse selectItems(UUID userId, List<UUID> variantIds);
    CartResponse deselectItems(UUID userId, List<UUID> variantIds);
    CartSnapshot getSnapshot(UUID userId);
    CartSnapshot getSelectedSnapshot(UUID userId);
    void clearSnapshotIfVersionUnchanged(UUID userId, long version);
}
