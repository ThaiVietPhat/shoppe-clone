package com.shopee.monolith.modules.media.dto.internal;

import lombok.Builder;

@Builder
public record MediaFileData(
        byte[] bytes,
        String contentType
) {
}
