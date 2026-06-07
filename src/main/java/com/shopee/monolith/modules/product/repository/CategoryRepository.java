package com.shopee.monolith.modules.product.repository;

import com.shopee.monolith.modules.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByParentId(UUID parentId);

    @Query("SELECT c FROM Category c WHERE c.id = :id OR c.parentId = :id ORDER BY c.name")
    List<Category> findByIdOrParentId(@Param("id") UUID id);

    Optional<Category> findByName(String name);
}
