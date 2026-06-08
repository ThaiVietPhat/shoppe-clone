package com.shopee.monolith.modules.media.repository;

import com.shopee.monolith.modules.media.entity.MediaAsset;
import com.shopee.monolith.modules.media.entity.MediaOwnerType;
import com.shopee.monolith.modules.media.entity.MediaPurpose;
import com.shopee.monolith.modules.media.entity.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Optional<MediaAsset> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<MediaAsset> findByIdAndStatus(UUID id, MediaStatus status);

    Optional<MediaAsset> findByObjectKeyAndStatus(String objectKey, MediaStatus status);

    List<MediaAsset> findAllByOwnerIdAndStatus(UUID ownerId, MediaStatus status);

    Optional<MediaAsset> findFirstByOwnerIdAndOwnerTypeAndPurposeAndStatusOrderByCreatedAtDesc(
            UUID ownerId,
            MediaOwnerType ownerType,
            MediaPurpose purpose,
            MediaStatus status);

    boolean existsByObjectKey(String objectKey);
}
