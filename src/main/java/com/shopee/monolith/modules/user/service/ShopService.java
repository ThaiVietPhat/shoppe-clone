package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.dto.request.CreateShopRequest;
import com.shopee.monolith.modules.user.dto.request.UpdateShopRequest;
import com.shopee.monolith.modules.user.dto.response.ShopResponse;

import java.util.Optional;
import java.util.UUID;

public interface ShopService {

    ShopResponse createShop(UUID ownerId, CreateShopRequest request);

    ShopResponse getShopByOwnerId(UUID ownerId);

    ShopResponse getShopById(UUID shopId);

    ShopResponse updateShop(UUID ownerId, UpdateShopRequest request);

    Optional<ShopLookupData> findShopLookupDataById(UUID shopId);

    Optional<ShopLookupData> findShopLookupDataByOwnerId(UUID ownerId);
}
