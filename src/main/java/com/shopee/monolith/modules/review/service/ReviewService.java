package com.shopee.monolith.modules.review.service;

import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.review.dto.request.CreateReviewRequest;
import com.shopee.monolith.modules.review.dto.request.UpdateReviewRequest;
import com.shopee.monolith.modules.review.dto.response.ProductReviewListResponse;
import com.shopee.monolith.modules.review.dto.response.ReviewResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {

    /**
     * Creates a review for one delivered/completed order item owned by the buyer.
     * One review per order item; duplicates are rejected with REVIEW_ALREADY_EXISTS.
     */
    ReviewResponse createReview(UUID buyerId, CreateReviewRequest request);

    /** Updates rating/comment of the buyer's own review. */
    ReviewResponse updateReview(UUID buyerId, UUID reviewId, UpdateReviewRequest request);

    /** Public: rating summary plus a page of reviews for a product, newest first. */
    ProductReviewListResponse listProductReviews(UUID productId, Pageable pageable);

    /** Current buyer's own reviews, newest first. */
    PagedResponse<ReviewResponse> listMyReviews(UUID buyerId, Pageable pageable);
}
