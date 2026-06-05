package com.shopee.monolith.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Generic paged response wrapper")
public record PagedResponse<T>(
        @Schema(description = "List of items in the current page")
        List<T> items,

        @Schema(description = "Current page index (0-indexed)", example = "0")
        int page,

        @Schema(description = "Number of items per page", example = "20")
        int size,

        @Schema(description = "Total number of elements across all pages", example = "105")
        long totalElements,

        @Schema(description = "Total number of pages", example = "6")
        int totalPages,

        @Schema(description = "Indicates if this is the last page", example = "false")
        boolean last
) {
    public static <T> PagedResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
        return PagedResponse.<T>builder()
                .items(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .last(springPage.isLast())
                .build();
    }

    public static <T, R> PagedResponse<R> from(org.springframework.data.domain.Page<T> springPage, List<R> transformedItems) {
        return PagedResponse.<R>builder()
                .items(transformedItems)
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .last(springPage.isLast())
                .build();
    }
}
