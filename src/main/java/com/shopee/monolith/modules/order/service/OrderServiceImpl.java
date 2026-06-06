package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshot;
import com.shopee.monolith.modules.cart.dto.internal.CartSnapshotItem;
import com.shopee.monolith.modules.cart.service.CartService;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.modules.order.entity.IdempotencyKey;
import com.shopee.monolith.modules.order.model.IdempotencyStatus;
import com.shopee.monolith.modules.order.repository.IdempotencyKeyRepository;
import com.shopee.monolith.modules.user.dto.response.AddressResponse;
import com.shopee.monolith.modules.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CheckoutProcessor checkoutProcessor;
    private final CartService cartService;
    private final ProductService productService;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AddressService addressService;
    private final ObjectMapper objectMapper;

    @Override
    public CheckoutResponse checkout(UUID buyerId, CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }

        String requestHash = computeRequestHash(request);
        UUID keyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(1));

        // Look up existing idempotency key first to return cached responses even if the cart is already cleared
        Optional<IdempotencyKey> existingKeyOpt = idempotencyKeyRepository.findByActorIdAndOperationAndIdempotencyKey(buyerId, "CHECKOUT", idempotencyKey);
        if (existingKeyOpt.isPresent()) {
            IdempotencyKey existing = existingKeyOpt.get();
            if (existing.getExpiresAt().isAfter(Instant.now())) {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
                }
                if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                    throw new AppException(ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING);
                }
                try {
                    return objectMapper.readValue(existing.getResponseBody(), CheckoutResponse.class);
                } catch (Exception e) {
                    log.error("Failed to deserialize cached checkout response in pre-check", e);
                    throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }

        // Resolve address details OUTSIDE DB transaction
        AddressResponse address;
        if (request.addressId() != null) {
            address = addressService.findAddressByIdAndUserId(request.addressId(), buyerId)
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        } else {
            address = addressService.findDefaultAddress(buyerId)
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        }

        if (address.wardCode() == null || address.wardCode().isBlank() ||
                address.districtCode() == null || address.districtCode().isBlank() ||
                address.provinceCode() == null || address.provinceCode().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        // Proceed with checkout - Read cart snapshot from Redis OUTSIDE the DB transaction
        CartSnapshot cartSnapshot = cartService.getSnapshot(buyerId);
        if (cartSnapshot == null || cartSnapshot.items().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        // Resolve variants and group by shopId OUTSIDE the DB transaction
        List<CartItemWithDetails> resolvedItems = new ArrayList<>();
        for (CartSnapshotItem item : cartSnapshot.items()) {
            VariantLookupData variant = productService.findVariantLookupDataById(item.variantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

            ProductLookupData product = productService.findProductLookupDataById(variant.productId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

            resolvedItems.add(new CartItemWithDetails(item, variant, product));
        }

        // Enter short DB transaction via CheckoutProcessor
        long cartVersion = cartSnapshot.version();
        CheckoutResponse response = checkoutProcessor.processCheckout(
                buyerId,
                address,
                idempotencyKey,
                requestHash,
                keyId,
                expiresAt,
                resolvedItems,
                () -> cartService.clearSnapshotIfVersionUnchanged(buyerId, cartVersion)
        );

        return response;
    }

    private String computeRequestHash(CheckoutRequest request) {
        try {
            String canonical = request.addressId() != null ? request.addressId().toString() : "null";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute request hash", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public record CartItemWithDetails(
            CartSnapshotItem cartItem,
            VariantLookupData variant,
            ProductLookupData product
    ) {}
}
