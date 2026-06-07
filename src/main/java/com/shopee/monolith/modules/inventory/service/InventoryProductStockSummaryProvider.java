package com.shopee.monolith.modules.inventory.service;

import com.shopee.monolith.modules.inventory.entity.Inventory;
import com.shopee.monolith.modules.inventory.repository.InventoryRepository;
import com.shopee.monolith.modules.product.dto.internal.ProductStockSummaryDto;
import com.shopee.monolith.modules.product.service.ProductStockSummaryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryProductStockSummaryProvider implements ProductStockSummaryProvider {

    private final InventoryRepository inventoryRepository;

    @Override
    public Map<UUID, ProductStockSummaryDto> getStockSummariesByVariantIds(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findAllByVariantIdIn(variantIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getVariantId,
                        inventory -> ProductStockSummaryDto.builder()
                                .variantId(inventory.getVariantId())
                                .availableStock(inventory.getAvailableStock())
                                .reservedStock(inventory.getReservedStock())
                                .build()
                ));
    }
}
