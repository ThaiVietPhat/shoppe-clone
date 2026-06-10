package com.shopee.monolith.modules.payment.service;

import com.shopee.monolith.modules.payment.config.VNPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VNPaySignatureVerifierTest {

    private VNPaySignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        VNPayProperties properties = new VNPayProperties();
        properties.setHashSecret("test-secret");
        verifier = new VNPaySignatureVerifier(properties);
    }

    private Map<String, String> signedParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "8f14e45f-ceea-467f-9c4e-1d2a3b4c5d6e");
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "12345678");
        params.put("vnp_SecureHash", verifier.sign(params));
        return params;
    }

    @Test
    void verifyWhenSignatureValidShouldReturnTrue() {
        assertTrue(verifier.verify(signedParams()));
    }

    @Test
    void verifyWhenPayloadTamperedShouldReturnFalse() {
        Map<String, String> params = signedParams();
        params.put("vnp_Amount", "99900000");
        assertFalse(verifier.verify(params));
    }

    @Test
    void verifyWhenSecureHashMissingShouldReturnFalse() {
        Map<String, String> params = signedParams();
        params.remove("vnp_SecureHash");
        assertFalse(verifier.verify(params));
    }

    @Test
    void verifyShouldIgnoreSecureHashTypeParamInHashData() {
        Map<String, String> params = signedParams();
        params.put("vnp_SecureHashType", "HMACSHA512");
        assertTrue(verifier.verify(params));
    }
}
