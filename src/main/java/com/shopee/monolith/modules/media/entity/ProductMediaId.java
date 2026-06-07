package com.shopee.monolith.modules.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductMediaId implements Serializable {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;
}
