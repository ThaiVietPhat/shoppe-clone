package com.shopee.monolith.modules.review.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.review.dto.request.CreateReviewRequest;
import com.shopee.monolith.modules.review.dto.request.UpdateReviewRequest;
import com.shopee.monolith.modules.review.dto.response.ProductReviewListResponse;
import com.shopee.monolith.modules.review.dto.response.ReviewResponse;
import com.shopee.monolith.modules.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review create/list/update APIs (delivered orders only)")
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;

    @Operation(
            summary = "Create a review for a delivered order item",
            description = "Only the buyer who owns the order can review, and only after the order is "
                    + "DELIVERED or COMPLETED. One review per order item.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Review created.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Order item not found or not owned by the caller.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Order not reviewable yet, or item already reviewed.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PostMapping("/reviews")
    public ApiResponse<ReviewResponse> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(reviewService.createReview(claims.userId(), request));
    }

    @Operation(
            summary = "Update own review",
            description = "Updates rating/comment of a review owned by the authenticated buyer.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Review updated.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Review not found or not owned by the caller.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @PatchMapping("/reviews/{reviewId}")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(reviewService.updateReview(claims.userId(), reviewId, request));
    }

    @Operation(
            summary = "List reviews of a product (public)",
            description = "Rating summary plus a page of reviews, newest first. No authentication required."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Reviews returned.")
    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<ProductReviewListResponse> listProductReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                reviewService.listProductReviews(productId, PageRequest.of(Math.max(page, 0), capSize(size))));
    }

    @Operation(
            summary = "List current buyer's reviews",
            description = "Paged list of the authenticated buyer's own reviews, newest first.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Reviews returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class)))
    @GetMapping("/reviews/me")
    public ApiResponse<PagedResponse<ReviewResponse>> listMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AccessTokenClaims claims) {
        requireAuthenticated(claims);
        return ApiResponse.success(
                reviewService.listMyReviews(claims.userId(), PageRequest.of(Math.max(page, 0), capSize(size))));
    }

    private int capSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private void requireAuthenticated(AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
