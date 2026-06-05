package com.shopee.monolith.modules.inventory.mapper;

import com.shopee.monolith.modules.inventory.entity.Inventory;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    InventoryResponse toResponse(Inventory inventory);
}
