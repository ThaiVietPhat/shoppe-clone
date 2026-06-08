package com.shopee.monolith.modules.media.dto.response;

import com.shopee.monolith.modules.media.dto.internal.MediaOwnerTypeCode;
import com.shopee.monolith.modules.media.dto.internal.MediaPurposeCode;
import com.shopee.monolith.modules.media.dto.internal.MediaStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Media asset metadata response")
public record MediaAssetResponse(
        @Schema(description = "Media asset unique ID", example = "3f1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Owner entity ID (shopId or userId)", example = "4a123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID ownerId,

        @Schema(description = "Owner entity type", example = "SHOP")
        MediaOwnerTypeCode ownerType,

        @Schema(description = "Intended usage of this media asset", example = "PRODUCT_IMAGE")
        MediaPurposeCode purpose,

        @Schema(description = "Unique object key in storage (not original filename)", example = "3f1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d.jpg")
        String objectKey,

        @Schema(description = "Public accessible URL", example = "http://localhost:8080/api/media/files/3f1deb4d-3b7d.jpg")
        String publicUrl,

        @Schema(description = "MIME content type", example = "image/jpeg")
        String contentType,

        @Schema(description = "File size in bytes", example = "204800")
        long sizeBytes,

        @Schema(description = "Image width in pixels (null for non-image)", example = "1200")
        Integer width,

        @Schema(description = "Image height in pixels (null for non-image)", example = "900")
        Integer height,

        @Schema(description = "Asset lifecycle status", example = "READY")
        MediaStatusCode status,

        @Schema(description = "Timestamp when uploaded")
        Instant createdAt
) {}
