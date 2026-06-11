package com.shopee.monolith.modules.payment.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.order.service.CheckoutSettlementService;
import com.shopee.monolith.modules.payment.entity.PaymentAttempt;
import com.shopee.monolith.modules.payment.model.PaymentAttemptStatus;
import com.shopee.monolith.modules.payment.model.PaymentMethod;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import com.shopee.monolith.modules.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VNPayWebhookServiceTest {

    @Mock
    private VNPaySignatureVerifier signatureVerifier;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private CheckoutSettlementService checkoutSettlementService;

    @InjectMocks
    private VNPayWebhookService webhookService;

    private final UUID attemptId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    private PaymentAttempt attempt;

    @BeforeEach
    void setUp() {
        attempt = PaymentAttempt.builder()
                .id(attemptId)
                .checkoutSessionId(sessionId)
                .method(PaymentMethod.VNPAY)
                .status(PaymentAttemptStatus.PENDING)
                .amount(new BigDecimal("150000.00"))
                .currency("VND")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        lenient().when(signatureVerifier.buildHashData(any())).thenReturn("hash-data");
    }

    private Map<String, String> successParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", attemptId.toString());
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "98765432");
        return params;
    }

    @Test
    void processWebhookWhenSignatureInvalidShouldThrowBeforeAnyProcessing() {
        when(signatureVerifier.verify(any())).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> webhookService.processWebhook(successParams()));

        assertEquals(ErrorCode.INVALID_WEBHOOK_SIGNATURE, exception.getErrorCode());
        verify(paymentAttemptRepository, never()).findByIdForUpdate(any());
        verify(webhookEventRepository, never()).tryClaim(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void processWebhookWhenSuccessShouldConfirmSessionAndSucceedAttempt() {
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(1);
        when(checkoutSettlementService.confirmCheckoutSession(sessionId, "VNPAY")).thenReturn(true);

        VNPayWebhookService.WebhookResult result = webhookService.processWebhook(successParams());

        assertEquals(VNPayWebhookService.WebhookResult.PROCESSED, result);
        assertEquals(PaymentAttemptStatus.SUCCEEDED, attempt.getStatus());
        assertEquals("98765432", attempt.getExternalTxId());
    }

    @Test
    void processWebhookWhenDuplicateEventShouldNoOpWithoutStateChange() {
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(0);

        VNPayWebhookService.WebhookResult result = webhookService.processWebhook(successParams());

        assertEquals(VNPayWebhookService.WebhookResult.DUPLICATE, result);
        assertEquals(PaymentAttemptStatus.PENDING, attempt.getStatus());
        verify(checkoutSettlementService, never()).confirmCheckoutSession(any(), anyString());
    }

    @Test
    void processWebhookWhenAmountMismatchShouldRequireReconciliationWithoutConfirm() {
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        Map<String, String> params = successParams();
        params.put("vnp_Amount", "99900000");
        webhookService.processWebhook(params);

        assertEquals(PaymentAttemptStatus.REQUIRES_RECONCILIATION, attempt.getStatus());
        assertEquals("AMOUNT_MISMATCH", attempt.getReconciliationReason());
        verify(checkoutSettlementService, never()).confirmCheckoutSession(any(), anyString());
    }

    @Test
    void processWebhookWhenLateSuccessAfterReleaseShouldRequireReconciliation() {
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(1);
        when(checkoutSettlementService.confirmCheckoutSession(sessionId, "VNPAY")).thenReturn(false);

        webhookService.processWebhook(successParams());

        assertEquals(PaymentAttemptStatus.REQUIRES_RECONCILIATION, attempt.getStatus());
        assertEquals("SESSION_NOT_PAYABLE", attempt.getReconciliationReason());
    }

    @Test
    void processWebhookWhenExpiredAttemptReceivesSuccessShouldFlagReconciliation() {
        attempt.expire();
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        webhookService.processWebhook(successParams());

        assertEquals(PaymentAttemptStatus.REQUIRES_RECONCILIATION, attempt.getStatus());
        assertEquals("LATE_SUCCESS_AFTER_EXPIRED", attempt.getReconciliationReason());
        verify(checkoutSettlementService, never()).confirmCheckoutSession(any(), anyString());
    }

    @Test
    void processWebhookWhenAlreadySucceededShouldNoOp() {
        attempt.succeed("prev-tx-id");
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        webhookService.processWebhook(successParams());

        assertEquals(PaymentAttemptStatus.SUCCEEDED, attempt.getStatus());
        verify(checkoutSettlementService, never()).confirmCheckoutSession(any(), anyString());
    }

    @Test
    void processWebhookWhenFailureResponseCodeShouldFailAttemptAndReleaseSession() {
        when(signatureVerifier.verify(any())).thenReturn(true);
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(webhookEventRepository.tryClaim(any(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        Map<String, String> params = successParams();
        params.put("vnp_ResponseCode", "24");
        webhookService.processWebhook(params);

        assertEquals(PaymentAttemptStatus.FAILED, attempt.getStatus());
        verify(checkoutSettlementService).markCheckoutPaymentFailed(sessionId);
    }
}
