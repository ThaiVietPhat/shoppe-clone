package com.shopee.monolith.modules.product.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "path", nullable = false, length = 2000)
    private String path;

    public void update(UUID parentId, String name) {
        this.parentId = parentId;
        this.name = name;
    }

    public void updatePath(String path) {
        this.path = path;
    }

    @PrePersist
    @PreUpdate
    void ensurePath() {
        if (path == null || path.isBlank()) {
            path = name;
        }
    }
}
