package com.shopee.monolith.modules.payment.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app.payment.vnpay")
@Validated
@Getter
@Setter
public class VNPayProperties {

    /** VNPay sandbox terminal code (vnp_TmnCode). */
    @NotBlank
    private String tmnCode = "SANDBOX";

    /** HMAC-SHA512 hash secret shared with VNPay. */
    @NotBlank
    private String hashSecret = "sandbox-secret-change-me";

    /** VNPay sandbox payment gateway URL. */
    @NotBlank
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    /** Backend return URL VNPay redirects the browser to after payment. */
    @NotBlank
    private String returnUrl = "http://localhost:8080/api/payments/return/vnpay";

    /** Frontend page the return handler redirects to with checkoutSessionId. */
    @NotBlank
    private String frontendReturnUrl = "http://localhost:3000/payment/return";
}
