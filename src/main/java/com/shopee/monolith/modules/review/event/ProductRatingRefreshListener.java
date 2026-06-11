package com.shopee.monolith.modules.review.event;

import com.shopee.monolith.modules.product.service.ProductService;
import com.shopee.monolith.modules.review.service.ReviewServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refreshes the product rating read model after a review commit.
 * Idempotent: always recomputes the full aggregate from the reviews table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductRatingRefreshListener {

    private final ReviewServiceImpl reviewService;
    private final ProductService productService;

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReviewSubmittedEvent event) {
        try {
            ReviewServiceImpl.RatingSummary summary = reviewService.aggregateRating(event.productId());
            productService.refreshRatingSummary(event.productId(), summary.avg(), summary.count());
            log.info("Refreshed rating summary for product {}: avg={}, count={}",
                    event.productId(), summary.avg(), summary.count());
        } catch (Exception e) {
            log.error("Failed to refresh rating summary for product {}", event.productId());
            throw new RuntimeException("Rating refresh failed for product " + event.productId(), e);
        }
    }
}
