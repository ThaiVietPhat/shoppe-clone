package com.shopee.monolith.modules.media.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.media.dto.internal.MediaOwnerTypeCode;
import com.shopee.monolith.modules.media.dto.internal.MediaPurposeCode;
import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.entity.MediaAsset;
import com.shopee.monolith.modules.media.entity.MediaOwnerType;
import com.shopee.monolith.modules.media.entity.MediaPurpose;
import com.shopee.monolith.modules.media.entity.MediaStatus;
import com.shopee.monolith.modules.media.entity.ProductMedia;
import com.shopee.monolith.modules.media.entity.ProductMediaId;
import com.shopee.monolith.modules.media.mapper.MediaMapper;
import com.shopee.monolith.modules.media.repository.MediaAssetRepository;
import com.shopee.monolith.modules.media.repository.ProductMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private ProductMediaRepository productMediaRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MediaMapper mediaMapper;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(
                mediaAssetRepository,
                productMediaRepository,
                storageService,
                mediaMapper
        );
        ReflectionTestUtils.setField(mediaService, "maxImageSizeBytes", 1024L);
    }

    @Test
    void uploadImageWhenHeaderIsWrongShouldStoreDetectedContentType() {
        UUID ownerId = UUID.randomUUID();
        MediaAssetResponse response = MediaAssetResponse.builder()
                .ownerId(ownerId)
                .contentType("image/png")
                .build();

        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getPublicUrl(anyString())).thenReturn("http://localhost:8080/api/media/files/test.png");
        when(mediaMapper.toResponse(any(MediaAsset.class), anyString())).thenReturn(response);

        MediaAssetResponse result = mediaService.uploadImage(
                ownerId,
                MediaOwnerTypeCode.SHOP,
                MediaPurposeCode.PRODUCT_IMAGE,
                "product.png",
                PNG_BYTES,
                "text/plain"
        );

        ArgumentCaptor<MediaAsset> assetCaptor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(assetCaptor.capture());
        verify(storageService).store(eq(PNG_BYTES), anyString(), eq("image/png"));
        assertEquals("image/png", assetCaptor.getValue().getContentType());
        assertEquals(response, result);
    }

    @Test
    void uploadImageWhenExtensionDoesNotMatchBytesShouldThrowException() {
        AppException ex = assertThrows(AppException.class, () -> mediaService.uploadImage(
                UUID.randomUUID(),
                MediaOwnerTypeCode.SHOP,
                MediaPurposeCode.PRODUCT_IMAGE,
                "product.jpg",
                PNG_BYTES,
                "image/jpeg"
        ));

        assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
        verify(storageService, never()).store(any(), anyString(), anyString());
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void uploadImageWhenMetadataSaveFailsShouldDeleteStoredObject() {
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> mediaService.uploadImage(
                UUID.randomUUID(),
                MediaOwnerTypeCode.SHOP,
                MediaPurposeCode.PRODUCT_IMAGE,
                "product.png",
                PNG_BYTES,
                "image/png"
        ));

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(eq(PNG_BYTES), objectKeyCaptor.capture(), eq("image/png"));
        verify(storageService).delete(objectKeyCaptor.getValue());
    }

    @Test
    void getMediaByIdWhenMediaIsDeletedShouldThrowNotFound() {
        UUID mediaId = UUID.randomUUID();
        when(mediaAssetRepository.findByIdAndStatus(mediaId, MediaStatus.READY)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> mediaService.getMediaById(mediaId));

        assertEquals(ErrorCode.MEDIA_NOT_FOUND, ex.getErrorCode());
        verify(storageService, never()).getPublicUrl(anyString());
    }

    @Test
    void attachToProductWhenMediaBelongsToAnotherShopShouldThrowException() {
        UUID shopId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        when(mediaAssetRepository.findByIdAndOwnerId(mediaId, shopId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> mediaService.attachToProduct(
                UUID.randomUUID(),
                shopId,
                productId,
                mediaId,
                0,
                true
        ));

        assertEquals(ErrorCode.MEDIA_OWNERSHIP_VIOLATION, ex.getErrorCode());
        verify(productMediaRepository, never()).save(any());
    }

    @Test
    void attachToProductWhenCoverShouldClearExistingCoverAndSaveLink() {
        UUID shopId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.builder()
                .ownerId(shopId)
                .ownerType(MediaOwnerType.SHOP)
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .objectKey("asset.png")
                .contentType("image/png")
                .sizeBytes(PNG_BYTES.length)
                .status(MediaStatus.READY)
                .build();

        when(mediaAssetRepository.findByIdAndOwnerId(mediaId, shopId)).thenReturn(Optional.of(asset));
        when(productMediaRepository.findByIdProductIdAndIdMediaId(productId, mediaId)).thenReturn(Optional.empty());

        mediaService.attachToProduct(UUID.randomUUID(), shopId, productId, mediaId, 0, true);

        verify(productMediaRepository).clearCoverByProductId(productId);
        verify(productMediaRepository).saveAndFlush(any());
    }

    @Test
    void attachToProductWhenCoverRaceViolatesUniqueIndexShouldThrowConflict() {
        UUID shopId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.builder()
                .ownerId(shopId)
                .ownerType(MediaOwnerType.SHOP)
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .objectKey("asset.png")
                .contentType("image/png")
                .sizeBytes(PNG_BYTES.length)
                .status(MediaStatus.READY)
                .build();

        when(mediaAssetRepository.findByIdAndOwnerId(mediaId, shopId)).thenReturn(Optional.of(asset));
        when(productMediaRepository.findByIdProductIdAndIdMediaId(productId, mediaId)).thenReturn(Optional.empty());
        when(productMediaRepository.saveAndFlush(any(ProductMedia.class)))
                .thenThrow(new DataIntegrityViolationException("uq_product_media_cover"));

        AppException ex = assertThrows(AppException.class, () -> mediaService.attachToProduct(
                UUID.randomUUID(), shopId, productId, mediaId, 0, true));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void attachToProductWhenMediaIsShopLogoShouldThrowException() {
        UUID shopId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.builder()
                .ownerId(shopId)
                .ownerType(MediaOwnerType.SHOP)
                .purpose(MediaPurpose.SHOP_LOGO)
                .objectKey("logo.png")
                .contentType("image/png")
                .sizeBytes(PNG_BYTES.length)
                .status(MediaStatus.READY)
                .build();

        when(mediaAssetRepository.findByIdAndOwnerId(mediaId, shopId)).thenReturn(Optional.of(asset));

        AppException ex = assertThrows(AppException.class, () -> mediaService.attachToProduct(
                UUID.randomUUID(), shopId, productId, mediaId, 0, true));

        assertEquals(ErrorCode.MEDIA_OWNERSHIP_VIOLATION, ex.getErrorCode());
        verify(productMediaRepository, never()).clearCoverByProductId(productId);
        verify(productMediaRepository, never()).saveAndFlush(any());
    }

    @Test
    void replaceProductMediaWhenDuplicateIdsShouldKeepSingleCoverAttachment() {
        UUID shopId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.builder()
                .ownerId(shopId)
                .ownerType(MediaOwnerType.SHOP)
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .objectKey("asset.png")
                .contentType("image/png")
                .sizeBytes(PNG_BYTES.length)
                .status(MediaStatus.READY)
                .build();

        when(mediaAssetRepository.findByIdAndOwnerId(mediaId, shopId)).thenReturn(Optional.of(asset));
        when(productMediaRepository.findByIdProductIdAndIdMediaId(productId, mediaId)).thenReturn(Optional.empty());

        mediaService.replaceProductMedia(UUID.randomUUID(), shopId, productId, List.of(mediaId, mediaId));

        ArgumentCaptor<ProductMedia> productMediaCaptor = ArgumentCaptor.forClass(ProductMedia.class);
        verify(productMediaRepository).deleteAllByProductId(productId);
        verify(productMediaRepository).saveAndFlush(productMediaCaptor.capture());
        assertEquals(mediaId, productMediaCaptor.getValue().getId().getMediaId());
        assertEquals(0, productMediaCaptor.getValue().getSortOrder());
        assertEquals(true, productMediaCaptor.getValue().isCover());
    }

    @Test
    void listProductMediaShouldOnlyReturnReadyAssets() {
        UUID productId = UUID.randomUUID();
        UUID readyMediaId = UUID.randomUUID();
        UUID deletedMediaId = UUID.randomUUID();
        ProductMedia readyLink = ProductMedia.builder()
                .id(new ProductMediaId(productId, readyMediaId))
                .sortOrder(0)
                .cover(true)
                .build();
        ProductMedia deletedLink = ProductMedia.builder()
                .id(new ProductMediaId(productId, deletedMediaId))
                .sortOrder(1)
                .cover(false)
                .build();
        MediaAsset readyAsset = MediaAsset.builder()
                .id(readyMediaId)
                .ownerId(UUID.randomUUID())
                .ownerType(MediaOwnerType.SHOP)
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .objectKey("ready.png")
                .contentType("image/png")
                .sizeBytes(PNG_BYTES.length)
                .status(MediaStatus.READY)
                .build();
        MediaAsset deletedAsset = MediaAsset.builder()
                .id(deletedMediaId)
                .ownerId(UUID.randomUUID())
                .ownerType(MediaOwnerType.SHOP)
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .objectKey("deleted.png")
                .contentType("image/png")
                .sizeBytes(PNG_BYTES.length)
                .status(MediaStatus.DELETED)
                .build();

        when(productMediaRepository.findAllByIdProductIdOrderBySortOrder(productId))
                .thenReturn(List.of(readyLink, deletedLink));
        when(mediaAssetRepository.findAllById(List.of(readyMediaId, deletedMediaId)))
                .thenReturn(List.of(readyAsset, deletedAsset));
        when(storageService.getPublicUrl("ready.png")).thenReturn("http://localhost/media/ready.png");

        List<com.shopee.monolith.modules.media.dto.response.ProductMediaSummary> result =
                mediaService.listProductMedia(productId);

        assertEquals(1, result.size());
        assertEquals(readyMediaId, result.get(0).mediaId());
        assertEquals("http://localhost/media/ready.png", result.get(0).publicUrl());
        verify(storageService, never()).getPublicUrl("deleted.png");
    }
}
