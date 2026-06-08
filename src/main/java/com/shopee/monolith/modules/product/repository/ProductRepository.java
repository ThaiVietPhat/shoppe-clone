package com.shopee.monolith.modules.product.repository;

import com.shopee.monolith.modules.product.entity.Product;
import com.shopee.monolith.modules.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findAllByShopId(UUID shopId, Pageable pageable);

    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findAllByShopIdAndStatus(UUID shopId, ProductStatus status, Pageable pageable);

    Page<Product> findAllByShopIdAndStatusNot(UUID shopId, ProductStatus status, Pageable pageable);

    Optional<Product> findByIdAndStatus(UUID id, ProductStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.status = :status")
    Optional<Product> findByIdAndStatusForUpdate(@Param("id") UUID id, @Param("status") ProductStatus status);

    List<Product> findAllByShopIdAndStatus(UUID shopId, ProductStatus status);
}
