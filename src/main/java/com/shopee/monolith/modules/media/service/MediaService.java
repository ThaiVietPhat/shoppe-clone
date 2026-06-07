package com.shopee.monolith.modules.media.service;

import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.dto.response.ProductMediaSummary;
import com.shopee.monolith.modules.media.dto.internal.MediaFileData;
import com.shopee.monolith.modules.media.entity.MediaPurpose;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MediaService {

    /**
     * Upload and store an image file. Validates MIME bytes, extension and size.
     *
     * @param ownerId   ID of the owning entity (shopId or userId)
     * @param ownerType "SHOP" or "USER"
     * @param purpose   intended usage (PRODUCT_IMAGE, AVATAR, SHOP_LOGO)
     * @param filename  original client filename (for extension validation only)
     * @param bytes     raw file bytes
     * @param mimeType  declared content-type (verified against bytes magic)
     * @return persisted MediaAssetResponse with publicUrl
     */
    MediaAssetResponse uploadImage(UUID ownerId, String ownerType, MediaPurpose purpose,
                                   String filename, byte[] bytes, String mimeType);

    /**
     * Get media asset metadata by ID (public).
     */
    MediaAssetResponse getMediaById(UUID mediaId);

    MediaFileData loadFile(String objectKey);

    /**
     * Attach a media asset to a product. Validates ownership.
     * If isCover=true, clears existing cover flag first.
     *
     * @param shopOwnerId the authenticated seller's userId (must own the product's shop)
     * @param productShopId the shopId of the product being updated
     * @param productId   target product
     * @param mediaId     media asset to attach (must be owned by productShopId)
     * @param sortOrder   display order
     * @param isCover     if true, this becomes the cover image
     */
    void attachToProduct(UUID shopOwnerId, UUID productShopId, UUID productId,
                         UUID mediaId, int sortOrder, boolean isCover);

    void replaceProductMedia(UUID shopOwnerId, UUID productShopId, UUID productId, List<UUID> mediaIds);

    /**
     * Detach a media asset from a product. Validates shop ownership.
     */
    void detachFromProduct(UUID shopOwnerId, UUID productShopId, UUID productId, UUID mediaId);

    /**
     * List all media attached to a product, ordered by sort_order.
     */
    List<ProductMediaSummary> listProductMedia(UUID productId);

    /**
     * Batch load product media indexed by productId — used by list endpoints.
     */
    Map<UUID, List<ProductMediaSummary>> listProductMediaByProductIds(List<UUID> productIds);
}
