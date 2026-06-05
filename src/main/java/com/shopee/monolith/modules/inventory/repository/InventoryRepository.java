package com.shopee.monolith.modules.inventory.repository;

import com.shopee.monolith.modules.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByVariantId(UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId IN :variantIds ORDER BY i.variantId ASC")
    List<Inventory> findAllByVariantIdInForUpdate(@Param("variantIds") Collection<UUID> variantIds);
}
