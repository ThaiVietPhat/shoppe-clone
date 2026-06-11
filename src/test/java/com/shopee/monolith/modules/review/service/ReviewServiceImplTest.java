package com.shopee.monolith.modules.review.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.order.dto.internal.OrderItemReviewData;
import com.shopee.monolith.modules.order.service.BuyerOrderService;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import com.shopee.monolith.modules.review.dto.request.CreateReviewRequest;
import com.shopee.monolith.modules.review.dto.request.UpdateReviewRequest;
import com.shopee.monolith.modules.review.dto.response.ReviewResponse;
import com.shopee.monolith.modules.review.entity.Review;
import com.shopee.monolith.modules.review.event.ReviewSubmittedEvent;
import com.shopee.monolith.modules.review.mapper.ReviewMapper;
import com.shopee.monolith.modules.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BuyerOrderService buyerOrderService;
    @Mock
    private ProductService productService;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private UUID buyerId;
    private UUID orderItemId;
    private UUID variantId;
    private UUID productId;
    private CreateReviewRequest createRequest;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        createRequest = CreateReviewRequest.builder()
                .orderItemId(orderItemId)
                .rating(5)
                .comment("great")
                .build();
    }

    private OrderItemReviewData reviewData(boolean reviewable, UUID owner) {
        return OrderItemReviewData.builder()
                .orderItemId(orderItemId)
                .orderId(UUID.randomUUID())
                .buyerId(owner)
                .shopId(UUID.randomUUID())
                .variantId(variantId)
                .productName("p")
                .variantName("v")
                .reviewable(reviewable)
                .build();
    }

    @Test
    void createReviewWhenValidShouldSaveAndPublishEvent() {
        when(buyerOrderService.findOrderItemReviewData(orderItemId))
                .thenReturn(Optional.of(reviewData(true, buyerId)));
        when(reviewRepository.existsByOrderItemId(orderItemId)).thenReturn(false);
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(
                VariantLookupData.builder().id(variantId).productId(productId).build()));
        Review saved = Review.builder().orderItemId(orderItemId).productId(productId)
                .buyerId(buyerId).rating(5).build();
        when(reviewRepository.saveAndFlush(any(Review.class))).thenReturn(saved);
        when(reviewMapper.toResponse(saved)).thenReturn(mock(ReviewResponse.class));

        reviewService.createReview(buyerId, createRequest);

        ArgumentCaptor<ReviewSubmittedEvent> captor = ArgumentCaptor.forClass(ReviewSubmittedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo(productId);
    }

    @Test
    void createReviewWhenOrderItemMissingShouldThrowOrderNotFound() {
        when(buyerOrderService.findOrderItemReviewData(orderItemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(buyerId, createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void createReviewWhenNotOwnerShouldThrowOrderNotFound() {
        when(buyerOrderService.findOrderItemReviewData(orderItemId))
                .thenReturn(Optional.of(reviewData(true, UUID.randomUUID())));

        assertThatThrownBy(() -> reviewService.createReview(buyerId, createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void createReviewWhenOrderNotDeliveredShouldThrowNotReviewable() {
        when(buyerOrderService.findOrderItemReviewData(orderItemId))
                .thenReturn(Optional.of(reviewData(false, buyerId)));

        assertThatThrownBy(() -> reviewService.createReview(buyerId, createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_REVIEWABLE);
    }

    @Test
    void createReviewWhenAlreadyReviewedShouldThrowReviewAlreadyExists() {
        when(buyerOrderService.findOrderItemReviewData(orderItemId))
                .thenReturn(Optional.of(reviewData(true, buyerId)));
        when(reviewRepository.existsByOrderItemId(orderItemId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(buyerId, createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createReviewWhenConcurrentDuplicateLosesRaceShouldThrowReviewAlreadyExists() {
        when(buyerOrderService.findOrderItemReviewData(orderItemId))
                .thenReturn(Optional.of(reviewData(true, buyerId)));
        when(reviewRepository.existsByOrderItemId(orderItemId)).thenReturn(false);
        when(productService.findVariantLookupDataById(variantId)).thenReturn(Optional.of(
                VariantLookupData.builder().id(variantId).productId(productId).build()));
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> reviewService.createReview(buyerId, createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    void updateReviewWhenNotOwnerShouldThrowReviewNotFound() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder().buyerId(UUID.randomUUID()).productId(productId).rating(4).build();
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(
                buyerId, reviewId, UpdateReviewRequest.builder().rating(3).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void updateReviewWhenOwnerShouldUpdateAndPublishEvent() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder().buyerId(buyerId).productId(productId).rating(4).build();
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);
        when(reviewMapper.toResponse(review)).thenReturn(mock(ReviewResponse.class));

        reviewService.updateReview(buyerId, reviewId, UpdateReviewRequest.builder().rating(2).comment("meh").build());

        assertThat(review.getRating()).isEqualTo(2);
        assertThat(review.getComment()).isEqualTo("meh");
        verify(eventPublisher).publishEvent(any(ReviewSubmittedEvent.class));
    }

    @Test
    void aggregateRatingWhenNoReviewsShouldReturnZero() {
        when(reviewRepository.aggregateRatingByProductId(productId))
                .thenReturn(List.<Object[]>of(new Object[]{null, 0L}));

        ReviewServiceImpl.RatingSummary summary = reviewService.aggregateRating(productId);

        assertThat(summary.avg()).isEqualTo(BigDecimal.ZERO);
        assertThat(summary.count()).isZero();
    }

    @Test
    void aggregateRatingWhenReviewsExistShouldRoundAverage() {
        when(reviewRepository.aggregateRatingByProductId(productId))
                .thenReturn(List.<Object[]>of(new Object[]{4.6667, 3L}));

        ReviewServiceImpl.RatingSummary summary = reviewService.aggregateRating(productId);

        assertThat(summary.avg()).isEqualByComparingTo("4.67");
        assertThat(summary.count()).isEqualTo(3);
    }
}
