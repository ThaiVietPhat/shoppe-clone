package com.shopee.monolith.modules.media.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.media.dto.internal.MediaOwnerTypeCode;
import com.shopee.monolith.modules.media.dto.internal.MediaPurposeCode;
import com.shopee.monolith.modules.media.service.MediaService;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
