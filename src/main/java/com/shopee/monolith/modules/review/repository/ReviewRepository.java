package com.shopee.monolith.modules.review.repository;

import com.shopee.monolith.modules.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findAllByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    Page<Review> findAllByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

    boolean existsByOrderItemId(UUID orderItemId);

    /** Single row [avg(rating), count(*)] for a product; avg is null when no reviews exist. */
    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.productId = :productId")
    java.util.List<Object[]> aggregateRatingByProductId(@Param("productId") UUID productId);
}
