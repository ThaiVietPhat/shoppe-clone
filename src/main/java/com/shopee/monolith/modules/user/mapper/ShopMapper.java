package com.shopee.monolith.modules.user.mapper;

import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.dto.response.ShopResponse;
import com.shopee.monolith.modules.user.entity.Shop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShopMapper {

    @Mapping(target = "logo", ignore = true)
    ShopResponse toResponse(Shop shop);

    ShopLookupData toLookupData(Shop shop);
}
