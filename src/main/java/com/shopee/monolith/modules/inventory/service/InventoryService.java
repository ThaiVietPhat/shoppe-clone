package com.shopee.monolith.modules.inventory.service;

import com.shopee.monolith.modules.inventory.dto.command.ConfirmInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReleaseInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReserveInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryResponse getInventoryByVariantId(UUID variantId);

    InventoryResponse createInventory(UUID variantId, int initialStock);

    InventoryResponse updateAvailableStock(UUID variantId, int availableStock);

    void reserve(List<ReserveInventoryCommand> commands);

    void confirm(List<ConfirmInventoryCommand> commands);

    void release(List<ReleaseInventoryCommand> commands);
}
