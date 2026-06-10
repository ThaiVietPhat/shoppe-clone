package com.shopee.monolith.modules.order.repository;

import com.shopee.monolith.modules.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.checkoutSessionId = :checkoutSessionId order by o.id asc")
    List<Order> findAllByCheckoutSessionIdForUpdate(@Param("checkoutSessionId") UUID checkoutSessionId);

    List<Order> findAllByCheckoutSessionIdOrderByIdAsc(UUID checkoutSessionId);

    Page<Order> findAllByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

    Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}
