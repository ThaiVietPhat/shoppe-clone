package com.shopee.monolith.modules.inventory.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.inventory.dto.command.ConfirmInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReleaseInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.command.ReserveInventoryCommand;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;
import com.shopee.monolith.modules.inventory.entity.Inventory;
import com.shopee.monolith.modules.inventory.entity.InventoryMovement;
import com.shopee.monolith.modules.inventory.entity.InventoryMovementType;
import com.shopee.monolith.modules.inventory.mapper.InventoryMapper;
import com.shopee.monolith.modules.inventory.repository.InventoryMovementRepository;
import com.shopee.monolith.modules.inventory.repository.InventoryRepository;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private ProductService productService;

    @Mock
    private ShopService shopService;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private final UUID variantId = UUID.randomUUID();
    private final UUID otherVariantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID shopId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private Inventory inventory;
    private InventoryResponse inventoryResponse;
    private VariantLookupData variantLookup;
    private ProductLookupData productLookup;
    private ShopLookupData shopLookup;

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
                .productId(productId)
                .sku("VAR-123")
                .name("Standard Variant")
                .price(BigDecimal.TEN)
                .build();

        productLookup = ProductLookupData.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(UUID.randomUUID())
                .name("Demo Product")
                .build();

        shopLookup = ShopLookupData.builder()
                .id(shopId)
                .ownerId(userId)
                .name("Demo Shop")
                .build();
    }

    private void stubOwnershipSuccess() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(productService.findProductLookupDataById(productId)).thenReturn(Optional.of(productLookup));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
    }

    @Test
    void getInventoryByVariantIdWhenExistsShouldReturnResponse() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.getInventoryByVariantId(variantId, userId, Role.SELLER);

        assertEquals(inventoryResponse, result);
    }

    @Test
    void getInventoryByVariantIdWhenAdminShouldBypassOwnershipButStillCheckVariant() {
        // ADMIN bypasses shop-ownership check but NOT variant-existence check
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.getInventoryByVariantId(variantId, otherUserId, Role.ADMIN);

        assertEquals(inventoryResponse, result);
    }

    @Test
    void getInventoryByVariantIdWhenNonOwnerShouldThrowException() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(productService.findProductLookupDataById(productId)).thenReturn(Optional.of(productLookup));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.getInventoryByVariantId(variantId, otherUserId, Role.SELLER));
        assertEquals(ErrorCode.SHOP_OWNER_REQUIRED, ex.getErrorCode());
    }

    @Test
    void getInventoryByVariantIdWhenVariantNotFoundShouldThrowException() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.getInventoryByVariantId(variantId, userId, Role.SELLER));
        assertEquals(ErrorCode.VARIANT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getInventoryByVariantIdWhenProductNotFoundShouldThrowException() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(productService.findProductLookupDataById(productId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.getInventoryByVariantId(variantId, userId, Role.SELLER));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getInventoryByVariantIdWhenShopNotFoundShouldThrowException() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(productService.findProductLookupDataById(productId)).thenReturn(Optional.of(productLookup));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.getInventoryByVariantId(variantId, userId, Role.SELLER));
        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getInventoryByVariantIdWhenNotExistsShouldThrowException() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.getInventoryByVariantId(variantId, userId, Role.SELLER));
        assertEquals(ErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenValidShouldCreateAndReturnResponse() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.empty());
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.createInventory(variantId, 50, userId, Role.SELLER);

        assertEquals(inventoryResponse, result);
    }

    @Test
    void createInventoryWhenAdminAndVariantNotFoundShouldThrowVariantNotFound() {
        // ADMIN skips ownership check but NOT variant-existence check
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.createInventory(variantId, 10, otherUserId, Role.ADMIN));
        assertEquals(ErrorCode.VARIANT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenNegativeStockShouldThrowException() {
        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.createInventory(variantId, -1, userId, Role.SELLER));
        assertEquals(ErrorCode.INVALID_STOCK_QUANTITY, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenInventoryAlreadyExistsShouldThrowException() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(inventory));

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.createInventory(variantId, 50, userId, Role.SELLER));
        assertEquals(ErrorCode.INVENTORY_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenConstraintViolationShouldThrowAlreadyExists() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.empty());
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException(
                        "unique constraint: inventories_variant_id_key")
        );

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.createInventory(variantId, 50, userId, Role.SELLER));
        assertEquals(ErrorCode.INVENTORY_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void createInventoryWhenFkViolationShouldThrowVariantNotFound() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.empty());
        // FK violation message does NOT contain the unique constraint name
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException(
                        "violates foreign key constraint \"inventories_variant_id_fkey\"")
        );

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.createInventory(variantId, 50, userId, Role.SELLER));
        assertEquals(ErrorCode.VARIANT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void updateAvailableStockWhenValidShouldUpdateAndReturnResponse() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.updateAvailableStock(variantId, 100, userId, Role.SELLER);

        assertEquals(100, inventory.getAvailableStock());
        assertEquals(inventoryResponse, result);
    }

    @Test
    void updateAvailableStockWhenNegativeStockShouldThrowException() {
        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.updateAvailableStock(variantId, -5, userId, Role.SELLER));
        assertEquals(ErrorCode.INVALID_STOCK_QUANTITY, ex.getErrorCode());
    }

    @Test
    void updateAvailableStockWhenNotFoundShouldThrowException() {
        stubOwnershipSuccess();
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                inventoryService.updateAvailableStock(variantId, 10, userId, Role.SELLER));
        assertEquals(ErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void reserveWhenValidShouldDeductAvailableAndAddReserved() {
        Inventory otherInventory = Inventory.builder()
                .variantId(otherVariantId)
                .availableStock(30)
                .reservedStock(0)
                .build();

        List<ReserveInventoryCommand> commands = Arrays.asList(
                new ReserveInventoryCommand(variantId, 10),
                new ReserveInventoryCommand(otherVariantId, 5),
                new ReserveInventoryCommand(variantId, 5)
        );

        List<UUID> expectedSortedIds = Arrays.asList(variantId, otherVariantId).stream()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();

        when(inventoryRepository.findAllByVariantIdInForUpdate(expectedSortedIds))
                .thenReturn(Arrays.asList(inventory, otherInventory));

        inventoryService.reserve(commands);

        assertEquals(35, inventory.getAvailableStock());
        assertEquals(20, inventory.getReservedStock());
        assertEquals(25, otherInventory.getAvailableStock());
        assertEquals(5, otherInventory.getReservedStock());

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
                new ReserveInventoryCommand(variantId, 100)
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

        assertEquals(2, inventory.getReservedStock());
        assertEquals(50, inventory.getAvailableStock());
        verify(inventoryRepository).saveAll(any());
    }

    @Test
    void confirmWhenReservedInsufficientShouldThrowException() {
        List<ConfirmInventoryCommand> commands = Collections.singletonList(
                new ConfirmInventoryCommand(variantId, 10)
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

        assertEquals(2, inventory.getReservedStock());
        assertEquals(53, inventory.getAvailableStock());
        verify(inventoryRepository).saveAll(any());
    }

    @Test
    void releaseWhenReservedInsufficientShouldThrowException() {
        List<ReleaseInventoryCommand> commands = Collections.singletonList(
                new ReleaseInventoryCommand(variantId, 10)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        AppException ex = assertThrows(AppException.class, () -> inventoryService.release(commands));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveWhenValidShouldRecordReserveMovements() {
        List<ReserveInventoryCommand> commands = Collections.singletonList(
                new ReserveInventoryCommand(variantId, 10)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        inventoryService.reserve(commands);

        ArgumentCaptor<List<InventoryMovement>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryMovementRepository).saveAll(captor.capture());
        InventoryMovement movement = captor.getValue().get(0);
        assertEquals(InventoryMovementType.RESERVE, movement.getMovementType());
        assertEquals(10, movement.getQuantity());
        assertEquals(40, movement.getAvailableStockAfter());
        assertEquals(15, movement.getReservedStockAfter());
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmWhenValidShouldRecordConfirmMovements() {
        List<ConfirmInventoryCommand> commands = Collections.singletonList(
                new ConfirmInventoryCommand(variantId, 3)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        inventoryService.confirm(commands);

        ArgumentCaptor<List<InventoryMovement>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryMovementRepository).saveAll(captor.capture());
        InventoryMovement movement = captor.getValue().get(0);
        assertEquals(InventoryMovementType.CONFIRM, movement.getMovementType());
        assertEquals(3, movement.getQuantity());
        assertEquals(50, movement.getAvailableStockAfter());
        assertEquals(2, movement.getReservedStockAfter());
    }

    @Test
    @SuppressWarnings("unchecked")
    void releaseWhenValidShouldRecordReleaseMovements() {
        List<ReleaseInventoryCommand> commands = Collections.singletonList(
                new ReleaseInventoryCommand(variantId, 3)
        );

        when(inventoryRepository.findAllByVariantIdInForUpdate(Collections.singletonList(variantId)))
                .thenReturn(Collections.singletonList(inventory));

        inventoryService.release(commands);

        ArgumentCaptor<List<InventoryMovement>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryMovementRepository).saveAll(captor.capture());
        InventoryMovement movement = captor.getValue().get(0);
        assertEquals(InventoryMovementType.RELEASE, movement.getMovementType());
        assertEquals(3, movement.getQuantity());
        assertEquals(53, movement.getAvailableStockAfter());
        assertEquals(2, movement.getReservedStockAfter());
    }

    @Test
    void updateAvailableStockShouldRecordSignedDeltaMovement() {
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(variantLookup));
        when(productService.findProductLookupDataById(productId)).thenReturn(Optional.of(productLookup));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        inventoryService.updateAvailableStock(variantId, 30, userId, Role.SELLER);

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        InventoryMovement movement = captor.getValue();
        assertEquals(InventoryMovementType.STOCK_UPDATE, movement.getMovementType());
        assertEquals(-20, movement.getQuantity());
        assertEquals(30, movement.getAvailableStockAfter());
    }
}
