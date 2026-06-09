package com.shopee.monolith.modules.search.dto;

import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.product.dto.response.ProductCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wrapper around a paged product card list with a degraded indicator.
 * When {@code degraded=true} the results come from the PostgreSQL fallback
 * (Elasticsearch was unavailable); quality may be lower than normal ES scoring.
 */
@Schema(description = "Search result envelope with degraded-mode indicator")
public record SearchResponse(

        @Schema(description = "Paged list of matching product cards")
        PagedResponse<ProductCardResponse> products,

        @Schema(description = "True when results are served from the PostgreSQL fallback "
                + "because Elasticsearch was unavailable.", example = "false")
        boolean degraded,

        @Schema(description = "Human-readable reason for degraded mode, null when degraded=false",
                example = "ELASTICSEARCH_UNAVAILABLE")
        String degradedReason
) {
    public static SearchResponse ok(PagedResponse<ProductCardResponse> products) {
        return new SearchResponse(products, false, null);
    }

    public static SearchResponse degraded(PagedResponse<ProductCardResponse> products, String reason) {
        return new SearchResponse(products, true, reason);
    }
}
