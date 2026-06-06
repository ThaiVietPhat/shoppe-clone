package com.shopee.monolith.modules.order.repository;

import com.shopee.monolith.modules.order.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    @Query(value = "SELECT * FROM inventory_reservations " +
                   "WHERE checkout_session_id = :checkoutSessionId AND status = :status " +
                   "ORDER BY variant_id ASC " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<InventoryReservation> findAllByCheckoutSessionIdAndStatusForUpdate(
            @Param("checkoutSessionId") UUID checkoutSessionId,
            @Param("status") String status
    );
}
