package com.shopee.monolith.modules.inventory.repository;

import com.shopee.monolith.modules.inventory.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    Page<InventoryMovement> findAllByVariantIdOrderByCreatedAtDesc(UUID variantId, Pageable pageable);
}
