package com.shopee.monolith.modules.media.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.media.dto.internal.MediaOwnerTypeCode;
import com.shopee.monolith.modules.media.dto.internal.MediaPurposeCode;
import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.service.MediaService;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @Mock
    private ShopService shopService;

    @Mock
    private MultipartFile file;

    private MediaController mediaController;

    @BeforeEach
    void setUp() {
        mediaController = new MediaController(mediaService, shopService);
        ReflectionTestUtils.setField(mediaController, "maxImageSizeBytes", 1024L);
    }

    @Test
    void uploadImageWhenFileTooLargeShouldRejectBeforeReadingBytes() throws Exception {
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .build();
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1025L);

        AppException exception = assertThrows(AppException.class, () -> mediaController.uploadImage(
                file,
                userId,
                MediaOwnerTypeCode.USER,
                MediaPurposeCode.AVATAR,
                claims
        ));

        assertEquals(ErrorCode.FILE_TOO_LARGE, exception.getErrorCode());
        verify(file, never()).getBytes();
        verify(mediaService, never()).uploadImage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void uploadImageWhenUserOwnerAndValidShouldReturnMediaAssetResponse() throws IOException {
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .build();
        UUID mediaId = UUID.randomUUID();
        MediaAssetResponse assetResponse = new MediaAssetResponse(
                mediaId, userId, MediaOwnerTypeCode.USER, MediaPurposeCode.AVATAR,
                "uploads/avatar.jpg", "http://localhost/api/media/files/avatar.jpg",
                "image/jpeg", 512L, null, null, null, null);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(512L);
        when(file.getOriginalFilename()).thenReturn("avatar.jpg");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(file.getContentType()).thenReturn("image/jpeg");
        when(mediaService.uploadImage(eq(userId), eq(MediaOwnerTypeCode.USER),
                eq(MediaPurposeCode.AVATAR), any(), any(), any()))
                .thenReturn(assetResponse);

        ApiResponse<MediaAssetResponse> result = mediaController.uploadImage(
                file, userId, MediaOwnerTypeCode.USER, MediaPurposeCode.AVATAR, claims);

        assertNotNull(result);
        assertEquals(mediaId, result.data().id());
        assertEquals("uploads/avatar.jpg", result.data().objectKey());
    }

    @Test
    void uploadImageWhenUserOwnerIdMismatchShouldThrowOwnershipViolation() {
        UUID claimUserId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(claimUserId)
                .role(Role.BUYER)
                .build();

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(512L);

        AppException exception = assertThrows(AppException.class, () -> mediaController.uploadImage(
                file, differentUserId, MediaOwnerTypeCode.USER, MediaPurposeCode.AVATAR, claims));

        assertEquals(ErrorCode.MEDIA_OWNERSHIP_VIOLATION, exception.getErrorCode());
        verify(mediaService, never()).uploadImage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadImageWhenShopOwnerMismatchShouldThrowOwnershipViolation() {
        UUID claimUserId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        UUID differentOwnerId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(claimUserId)
                .role(Role.BUYER)
                .build();
        ShopLookupData shopLookup = ShopLookupData.builder()
                .id(shopId)
                .ownerId(differentOwnerId) // different from claimUserId
                .name("Other Shop")
                .build();

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(512L);
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));

        AppException exception = assertThrows(AppException.class, () -> mediaController.uploadImage(
                file, shopId, MediaOwnerTypeCode.SHOP, MediaPurposeCode.PRODUCT_IMAGE, claims));

        assertEquals(ErrorCode.MEDIA_OWNERSHIP_VIOLATION, exception.getErrorCode());
        verify(mediaService, never()).uploadImage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadImageWhenShopOwnerMatchesShouldCallService() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .build();
        ShopLookupData shopLookup = ShopLookupData.builder()
                .id(shopId)
                .ownerId(userId) // matches
                .name("My Shop")
                .build();
        MediaAssetResponse assetResponse = new MediaAssetResponse(
                UUID.randomUUID(), shopId, MediaOwnerTypeCode.SHOP, MediaPurposeCode.PRODUCT_IMAGE,
                "uploads/product.png", "http://localhost/api/media/files/product.png",
                "image/png", 512L, null, null, null, null);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(512L);
        when(file.getOriginalFilename()).thenReturn("product.png");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(file.getContentType()).thenReturn("image/png");
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(mediaService.uploadImage(eq(shopId), eq(MediaOwnerTypeCode.SHOP),
                eq(MediaPurposeCode.PRODUCT_IMAGE), any(), any(), any()))
                .thenReturn(assetResponse);

        ApiResponse<MediaAssetResponse> result = mediaController.uploadImage(
                file, shopId, MediaOwnerTypeCode.SHOP, MediaPurposeCode.PRODUCT_IMAGE, claims);

        assertNotNull(result);
        assertEquals("uploads/product.png", result.data().objectKey());
    }
}
