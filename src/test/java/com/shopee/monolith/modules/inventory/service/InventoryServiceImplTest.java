package com.shopee.monolith.modules.inventory.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.inventory.dto.command.ConfirmInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReleaseInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReserveInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;
import com.shopee.monolith.modules.inventory.entity.Inventory;
import com.shopee.monolith.modules.inventory.mapper.InventoryMapper;
import com.shopee.monolith.modules.inventory.repository.InventoryRepository;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private ProductService productService;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private final UUID variantId = UUID.randomUUID();
    private final UUID otherVariantId = UUID.randomUUID();
    private Inventory inventory;
    private InventoryResponse inventoryResponse;
    private VariantLookupData variantLookup;

    @BeforeEach
    void setUp() {
        inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .variantId(variantId)
                .availableStock(50)
                .reservedStock(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        inventoryResponse = InventoryResponse.builder()
                .id(inventory.getId())
                .variantId(variantId)
                .availableStock(50)
                .reservedStock(5)
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();

        variantLookup = VariantLookupData.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .sku("VAR-123")
                .name("Standard Variant")
                .price(BigDecimal.TEN)
                .build();
    }

    @Test
    void getInventoryByVariantIdWhenExistsShouldReturnResponse() {
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.getInventoryByVariantId(variantId);

        assertEquals(inventoryResponse, result);
    }

    @Test
    void getInventoryByVariantIdWhenNotExistsShouldThrowException() {
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> inventoryService.getInventoryByVariantId(variantId));
        assertEquals(ErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenValidShouldCreateAndReturnResponse() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.createInventory(variantId, 50);

        assertEquals(inventoryResponse, result);
    }

    @Test
    void createInventoryWhenNegativeStockShouldThrowException() {
        AppException ex = assertThrows(AppException.class, () -> inventoryService.createInventory(variantId, -1));
        assertEquals(ErrorCode.INVALID_STOCK_QUANTITY, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenVariantNotExistsShouldThrowException() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> inventoryService.createInventory(variantId, 50));
        assertEquals(ErrorCode.VARIANT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenInventoryAlreadyExistsShouldThrowException() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(inventory));

        AppException ex = assertThrows(AppException.class, () -> inventoryService.createInventory(variantId, 50));
        assertEquals(ErrorCode.INVENTORY_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void updateAvailableStockWhenValidShouldUpdateAndReturnResponse() {
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.updateAvailableStock(variantId, 100);

        assertEquals(100, inventory.getAvailableStock());
        assertEquals(inventoryResponse, result);
    }

    @Test
    void updateAvailableStockWhenNegativeStockShouldThrowException() {
        AppException ex = assertThrows(AppException.class, () -> inventoryService.updateAvailableStock(variantId, -5));
        assertEquals(ErrorCode.INVALID_STOCK_QUANTITY, ex.getErrorCode());
    }

    @Test
    void updateAvailableStockWhenNotFoundShouldThrowException() {
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> inventoryService.updateAvailableStock(variantId, 10));
        assertEquals(ErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void reserveWhenValidShouldDeductAvailableAndAddReserved() {
        Inventory otherInventory = Inventory.builder()
                .variantId(otherVariantId)
                .availableStock(30)
                .reservedStock(0)
                .build();

        // Pass duplicate variantIds to test consolidation
        List<ReserveInventoryCommand> commands = Arrays.asList(
                new ReserveInventoryCommand(variantId, 10),
                new ReserveInventoryCommand(otherVariantId, 5),
                new ReserveInventoryCommand(variantId, 5) // total 15 for variantId
        );

        // Sorting expectation: otherVariantId vs variantId
        List<UUID> expectedSortedIds = Arrays.asList(variantId, otherVariantId).stream()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();

        when(inventoryRepository.findAllByVariantIdInForUpdate(expectedSortedIds))
                .thenReturn(Arrays.asList(inventory, otherInventory));

        inventoryService.reserve(commands);

        assertEquals(35, inventory.getAvailableStock()); // 50 - 15
        assertEquals(20, inventory.getReservedStock());   // 5 + 15
        assertEquals(25, otherInventory.getAvailableStock()); // 30 - 5
        assertEquals(5, otherInventory.getReservedStock());   // 0 + 5

        verify(inventoryRepository).saveAll(any());
    }

    @Test
    void reserveWhenQuantityIsNegativeOrZeroShouldThrowException() {
        List<ReserveInventoryCommand> commands = Collections.singletonList(
                new ReserveInventoryCommand(variantId, 0)
        );

        AppException ex = assertThrows(AppException.class, () -> inventoryService.reserve(commands));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void reserveWhenInventoryNotFoundShouldThrowException() {
        List<ReserveInventoryCommand> commands = Collections.singletonList(
                new ReserveInventoryCommand(variantId, 5)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.emptyList());

        AppException ex = assertThrows(AppException.class, () -> inventoryService.reserve(commands));
        assertEquals(ErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void reserveWhenInsufficientStockShouldThrowException() {
        List<ReserveInventoryCommand> commands = Collections.singletonList(
                new ReserveInventoryCommand(variantId, 100) // exceeds 50 available
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        AppException ex = assertThrows(AppException.class, () -> inventoryService.reserve(commands));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
    }

    @Test
    void confirmWhenValidShouldDeductReserved() {
        List<ConfirmInventoryCommand> commands = Collections.singletonList(
                new ConfirmInventoryCommand(variantId, 3)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        inventoryService.confirm(commands);

        assertEquals(2, inventory.getReservedStock()); // 5 - 3
        assertEquals(50, inventory.getAvailableStock()); // unchanged
        verify(inventoryRepository).saveAll(any());
    }

    @Test
    void confirmWhenReservedInsufficientShouldThrowException() {
        List<ConfirmInventoryCommand> commands = Collections.singletonList(
                new ConfirmInventoryCommand(variantId, 10) // exceeds 5 reserved
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        AppException ex = assertThrows(AppException.class, () -> inventoryService.confirm(commands));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void releaseWhenValidShouldDeductReservedAndAddAvailable() {
        List<ReleaseInventoryCommand> commands = Collections.singletonList(
                new ReleaseInventoryCommand(variantId, 3)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        inventoryService.release(commands);

        assertEquals(2, inventory.getReservedStock()); // 5 - 3
        assertEquals(53, inventory.getAvailableStock()); // 50 + 3
        verify(inventoryRepository).saveAll(any());
    }

    @Test
    void releaseWhenReservedInsufficientShouldThrowException() {
        List<ReleaseInventoryCommand> commands = Collections.singletonList(
                new ReleaseInventoryCommand(variantId, 10) // exceeds 5 reserved
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        AppException ex = assertThrows(AppException.class, () -> inventoryService.release(commands));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }
}
