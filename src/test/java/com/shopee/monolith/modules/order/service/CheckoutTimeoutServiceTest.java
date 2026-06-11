package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class CheckoutTimeoutServiceTest {

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private CheckoutTimeoutProcessor timeoutProcessor;

    @Mock
    private com.shopee.monolith.common.observability.DemoMetrics demoMetrics;

    @InjectMocks
    private CheckoutTimeoutService checkoutTimeoutService;

    private CheckoutSession session1;
    private CheckoutSession session2;

    @BeforeEach
    void setUp() {
        session1 = CheckoutSession.builder()
                .id(UUID.randomUUID())
                .status(CheckoutSessionStatus.PENDING_PAYMENT)
                .expiresAt(Instant.now().minusSeconds(10))
                .build();

        session2 = CheckoutSession.builder()
                .id(UUID.randomUUID())
                .status(CheckoutSessionStatus.PENDING_PAYMENT)
                .expiresAt(Instant.now().minusSeconds(5))
                .build();
    }

    @Test
    void processExpiredCheckoutsWhenExpiredSessionsExistShouldCallProcessorForEach() {
        when(checkoutSessionRepository.findExpiredIds(
                eq(CheckoutSessionStatus.PENDING_PAYMENT),
                any(Instant.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(Arrays.asList(session1.getId(), session2.getId()));

        checkoutTimeoutService.processExpiredCheckouts(10);

        verify(timeoutProcessor, times(1)).processTimeout(eq(session1.getId()), any(Instant.class));
        verify(timeoutProcessor, times(1)).processTimeout(eq(session2.getId()), any(Instant.class));
    }

    @Test
    void processExpiredCheckoutsWhenNoExpiredSessionsShouldNotCallProcessor() {
        when(checkoutSessionRepository.findExpiredIds(
                eq(CheckoutSessionStatus.PENDING_PAYMENT),
                any(Instant.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(Collections.emptyList());

        checkoutTimeoutService.processExpiredCheckouts(10);

        verify(timeoutProcessor, never()).processTimeout(any(UUID.class), any(Instant.class));
    }

    @Test
    void processExpiredCheckoutsWhenProcessorThrowsExceptionShouldContinueProcessingOthers() {
        when(checkoutSessionRepository.findExpiredIds(
                eq(CheckoutSessionStatus.PENDING_PAYMENT),
                any(Instant.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(Arrays.asList(session1.getId(), session2.getId()));

        doThrow(new RuntimeException("Lock conflict or general error"))
                .when(timeoutProcessor).processTimeout(eq(session1.getId()), any(Instant.class));

        checkoutTimeoutService.processExpiredCheckouts(10);

        // Verify that timeoutProcessor was still called for session2 despite session1 throwing an error
        verify(timeoutProcessor, times(1)).processTimeout(eq(session1.getId()), any(Instant.class));
        verify(timeoutProcessor, times(1)).processTimeout(eq(session2.getId()), any(Instant.class));
    }
}
