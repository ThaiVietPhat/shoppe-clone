package com.shopee.monolith.modules.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Summary of a product media attachment")
public record ProductMediaSummary(
        @Schema(description = "Media asset ID")
        UUID mediaId,

        @Schema(description = "Public URL of the media asset")
        String publicUrl,

        @Schema(description = "Object key in storage")
        String objectKey,

        @Schema(description = "Content type")
        String contentType,

        @Schema(description = "Display order index")
        int sortOrder,

        @Schema(description = "Whether this is the cover/primary image")
        boolean cover
) {}
