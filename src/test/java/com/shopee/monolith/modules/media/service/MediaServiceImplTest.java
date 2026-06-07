package com.shopee.monolith.modules.media.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.entity.MediaAsset;
import com.shopee.monolith.modules.media.entity.MediaOwnerType;
import com.shopee.monolith.modules.media.entity.MediaPurpose;
import com.shopee.monolith.modules.media.entity.MediaStatus;
import com.shopee.monolith.modules.media.mapper.MediaMapper;
import com.shopee.monolith.modules.media.repository.MediaAssetRepository;
import com.shopee.monolith.modules.media.repository.ProductMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
                "SHOP",
                MediaPurpose.PRODUCT_IMAGE,
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
                "SHOP",
                MediaPurpose.PRODUCT_IMAGE,
                "product.jpg",
                PNG_BYTES,
                "image/jpeg"
        ));

        assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
        verify(storageService, never()).store(any(), anyString(), anyString());
        verify(mediaAssetRepository, never()).save(any());
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
        verify(productMediaRepository).save(any());
    }
}
