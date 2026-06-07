package com.shopee.monolith.modules.inventory.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.inventory.dto.command.ConfirmInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReleaseInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReserveInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.internal.InventoryStockSummary;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;
import com.shopee.monolith.modules.inventory.entity.Inventory;
import com.shopee.monolith.modules.inventory.mapper.InventoryMapper;
import com.shopee.monolith.modules.inventory.repository.InventoryRepository;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    // PostgreSQL auto-generates constraint names from table + column, matching DB migration V1
    private static final String UNIQUE_VARIANT_CONSTRAINT = "inventories_variant_id_key";

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final ProductService productService;
    private final ShopService shopService;

    /**
     * Validates that the variant exists — always runs for ALL callers including ADMIN.
     */
    private VariantLookupData validateVariantExists(UUID variantId) {
        return productService.findVariantLookupDataById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * Validates shop ownership for the variant's product.
     * ADMIN callers skip the ownership check but NOT the variant-existence check.
     */
    private void validateOwnership(UUID variantId, UUID currentUserId, Role currentRole) {
        VariantLookupData variant = validateVariantExists(variantId);

        if (currentRole == Role.ADMIN) {
            return;
        }

        ProductLookupData product = productService.findProductLookupDataById(variant.productId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        ShopLookupData shop = shopService.findShopLookupDataById(product.shopId())
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        if (!shop.ownerId().equals(currentUserId)) {
            throw new AppException(ErrorCode.SHOP_OWNER_REQUIRED);
        }
    }

    @Override
    public InventoryResponse getInventoryByVariantId(UUID variantId, UUID currentUserId, Role currentRole) {
        validateOwnership(variantId, currentUserId, currentRole);

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse createInventory(UUID variantId, int initialStock, UUID currentUserId, Role currentRole) {
        if (initialStock < 0) {
            throw new AppException(ErrorCode.INVALID_STOCK_QUANTITY);
        }

        // validateOwnership calls validateVariantExists first — variant is always checked, even for ADMIN
        validateOwnership(variantId, currentUserId, currentRole);

        if (inventoryRepository.findByVariantId(variantId).isPresent()) {
            throw new AppException(ErrorCode.INVENTORY_ALREADY_EXISTS);
        }

        try {
            Inventory inventory = Inventory.builder()
                    .variantId(variantId)
                    .availableStock(initialStock)
                    .reservedStock(0)
                    .build();

            inventory = inventoryRepository.saveAndFlush(inventory);
            return inventoryMapper.toResponse(inventory);
        } catch (DataIntegrityViolationException ex) {
            // Distinguish unique-constraint race (duplicate inventory) from FK violation (variant deleted mid-request)
            String message = ex.getMessage() != null ? ex.getMessage() : "";
            if (message.contains(UNIQUE_VARIANT_CONSTRAINT)) {
                throw new AppException(ErrorCode.INVENTORY_ALREADY_EXISTS);
            }
            throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public InventoryResponse updateAvailableStock(UUID variantId, int availableStock, UUID currentUserId, Role currentRole) {
        if (availableStock < 0) {
            throw new AppException(ErrorCode.INVALID_STOCK_QUANTITY);
        }

        validateOwnership(variantId, currentUserId, currentRole);

        Inventory inventory = inventoryRepository.findByVariantIdForUpdate(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.updateAvailableStock(availableStock);
        inventory = inventoryRepository.save(inventory);
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public void reserve(List<ReserveInventoryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (ReserveInventoryCommand cmd : commands) {
            if (cmd.quantity() <= 0) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }

        // Consolidate quantities per variantId to reduce lock footprint
        List<ReserveInventoryCommand> consolidated = commands.stream()
                .collect(Collectors.groupingBy(ReserveInventoryCommand::variantId,
                        Collectors.summingInt(ReserveInventoryCommand::quantity)))
                .entrySet().stream()
                .map(entry -> new ReserveInventoryCommand(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(cmd -> cmd.variantId().toString()))
                .toList();

        List<UUID> variantIds = consolidated.stream()
                .map(ReserveInventoryCommand::variantId)
                .toList();

        // Lock rows in deterministic (variantId ASC) order to prevent deadlock
        List<Inventory> inventories = inventoryRepository.findAllByVariantIdInForUpdate(variantIds);

        if (inventories.size() != variantIds.size()) {
            throw new AppException(ErrorCode.INVENTORY_NOT_FOUND);
        }

        Map<UUID, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getVariantId, Function.identity()));

        for (ReserveInventoryCommand cmd : consolidated) {
            Inventory inventory = inventoryMap.get(cmd.variantId());
            if (inventory == null) {
                throw new AppException(ErrorCode.INVENTORY_NOT_FOUND);
            }
            inventory.reserve(cmd.quantity());
        }

        inventoryRepository.saveAll(inventories);
    }

    @Override
    @Transactional
    public void confirm(List<ConfirmInventoryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (ConfirmInventoryCommand cmd : commands) {
            if (cmd.quantity() <= 0) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }

        List<ConfirmInventoryCommand> consolidated = commands.stream()
                .collect(Collectors.groupingBy(ConfirmInventoryCommand::variantId,
                        Collectors.summingInt(ConfirmInventoryCommand::quantity)))
                .entrySet().stream()
                .map(entry -> new ConfirmInventoryCommand(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(cmd -> cmd.variantId().toString()))
                .toList();

        List<UUID> variantIds = consolidated.stream()
                .map(ConfirmInventoryCommand::variantId)
                .toList();

        List<Inventory> inventories = inventoryRepository.findAllByVariantIdInForUpdate(variantIds);

        if (inventories.size() != variantIds.size()) {
            throw new AppException(ErrorCode.INVENTORY_NOT_FOUND);
        }

        Map<UUID, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getVariantId, Function.identity()));

        for (ConfirmInventoryCommand cmd : consolidated) {
            Inventory inventory = inventoryMap.get(cmd.variantId());
            if (inventory == null) {
                throw new AppException(ErrorCode.INVENTORY_NOT_FOUND);
            }
            inventory.confirm(cmd.quantity());
        }

        inventoryRepository.saveAll(inventories);
    }

    @Override
    @Transactional
    public void release(List<ReleaseInventoryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (ReleaseInventoryCommand cmd : commands) {
            if (cmd.quantity() <= 0) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }

        List<ReleaseInventoryCommand> consolidated = commands.stream()
                .collect(Collectors.groupingBy(ReleaseInventoryCommand::variantId,
                        Collectors.summingInt(ReleaseInventoryCommand::quantity)))
                .entrySet().stream()
                .map(entry -> new ReleaseInventoryCommand(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(cmd -> cmd.variantId().toString()))
                .toList();

        List<UUID> variantIds = consolidated.stream()
                .map(ReleaseInventoryCommand::variantId)
                .toList();

        List<Inventory> inventories = inventoryRepository.findAllByVariantIdInForUpdate(variantIds);

        if (inventories.size() != variantIds.size()) {
            throw new AppException(ErrorCode.INVENTORY_NOT_FOUND);
        }

        Map<UUID, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getVariantId, Function.identity()));

        for (ReleaseInventoryCommand cmd : consolidated) {
            Inventory inventory = inventoryMap.get(cmd.variantId());
            if (inventory == null) {
                throw new AppException(ErrorCode.INVENTORY_NOT_FOUND);
            }
            inventory.release(cmd.quantity());
        }

        inventoryRepository.saveAll(inventories);
    }

    @Override
    public Map<UUID, InventoryStockSummary> getStockSummariesByVariantIds(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findAllByVariantIdIn(variantIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getVariantId,
                        inv -> InventoryStockSummary.builder()
                                .variantId(inv.getVariantId())
                                .availableStock(inv.getAvailableStock())
                                .reservedStock(inv.getReservedStock())
                                .build()
                ));
    }
}
