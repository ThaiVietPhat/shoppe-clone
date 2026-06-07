package com.shopee.monolith.modules.product.repository;

import com.shopee.monolith.modules.product.entity.Product;
import com.shopee.monolith.modules.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findAllByShopId(UUID shopId, Pageable pageable);

    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findAllByShopIdAndStatus(UUID shopId, ProductStatus status, Pageable pageable);

    Page<Product> findAllByShopIdAndStatusNot(UUID shopId, ProductStatus status, Pageable pageable);

    Optional<Product> findByIdAndStatus(UUID id, ProductStatus status);

    List<Product> findAllByShopIdAndStatus(UUID shopId, ProductStatus status);
}
