package com.shopee.monolith.modules.media.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.media.dto.internal.MediaFileData;
import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.dto.response.ProductMediaSummary;
import com.shopee.monolith.modules.media.entity.MediaAsset;
import com.shopee.monolith.modules.media.entity.MediaOwnerType;
import com.shopee.monolith.modules.media.entity.MediaPurpose;
import com.shopee.monolith.modules.media.entity.MediaStatus;
import com.shopee.monolith.modules.media.entity.ProductMedia;
import com.shopee.monolith.modules.media.entity.ProductMediaId;
import com.shopee.monolith.modules.media.mapper.MediaMapper;
import com.shopee.monolith.modules.media.repository.MediaAssetRepository;
import com.shopee.monolith.modules.media.repository.ProductMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaServiceImpl implements MediaService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String WEBP_CONTENT_TYPE = "image/webp";

    // MIME magic byte signatures.
    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG  = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};

    private final MediaAssetRepository mediaAssetRepository;
    private final ProductMediaRepository productMediaRepository;
    private final StorageService storageService;
    private final MediaMapper mediaMapper;

    @Value("#{${app.media.max-file-size-mb:10} * 1048576}")
    private long maxImageSizeBytes;

    // ===================== Upload =====================

    @Override
    @Transactional
    public MediaAssetResponse uploadImage(UUID ownerId, String ownerType, MediaPurpose purpose,
                                          String filename, byte[] bytes, String mimeType) {
        validateFileSize(bytes);
        String detectedContentType = detectContentType(bytes);
        String extension = validateExtension(filename);
        validateExtensionMatchesContentType(extension, detectedContentType);

        String objectKey = UUID.randomUUID() + "." + extension;
        String checksum = computeSha256(bytes);

        MediaAsset asset = MediaAsset.builder()
                .ownerId(ownerId)
                .ownerType(MediaOwnerType.valueOf(ownerType))
                .purpose(purpose)
                .objectKey(objectKey)
                .originalFilename(sanitizeFilename(filename))
                .contentType(detectedContentType)
                .sizeBytes(bytes.length)
                .checksumSha256(checksum)
                .status(MediaStatus.READY)
                .build();

        storageService.store(bytes, objectKey, detectedContentType);
        try {
            asset = mediaAssetRepository.save(asset);
            String publicUrl = storageService.getPublicUrl(objectKey);
            return mediaMapper.toResponse(asset, publicUrl);
        } catch (RuntimeException e) {
            storageService.delete(objectKey);
            throw e;
        }
    }

    // ===================== Queries =====================

    @Override
    public MediaAssetResponse getMediaById(UUID mediaId) {
        MediaAsset asset = mediaAssetRepository.findByIdAndStatus(mediaId, MediaStatus.READY)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
        return mediaMapper.toResponse(asset, storageService.getPublicUrl(asset.getObjectKey()));
    }

    @Override
    public Optional<MediaAssetResponse> findLatestReadyMedia(UUID ownerId, String ownerType, MediaPurpose purpose) {
        return mediaAssetRepository.findFirstByOwnerIdAndOwnerTypeAndPurposeAndStatusOrderByCreatedAtDesc(
                        ownerId, MediaOwnerType.valueOf(ownerType), purpose, MediaStatus.READY)
                .map(asset -> mediaMapper.toResponse(asset, storageService.getPublicUrl(asset.getObjectKey())));
    }

    @Override
    public MediaFileData loadFile(String objectKey) {
        MediaAsset asset = mediaAssetRepository.findByObjectKeyAndStatus(objectKey, MediaStatus.READY)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
        return MediaFileData.builder()
                .bytes(storageService.load(objectKey))
                .contentType(asset.getContentType())
                .build();
    }

    @Override
    public List<ProductMediaSummary> listProductMedia(UUID productId) {
        return buildSummaries(productMediaRepository.findAllByIdProductIdOrderBySortOrder(productId));
    }

    @Override
    public Map<UUID, List<ProductMediaSummary>> listProductMediaByProductIds(List<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductMedia> all = productMediaRepository.findAllByIdProductIdInOrderBySortOrder(productIds);
        return all.stream()
                .collect(Collectors.groupingBy(
                        pm -> pm.getId().getProductId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                this::buildSummaries
                        )
                ));
    }

    // ===================== Product Attach/Detach =====================

    @Override
    @Transactional
    public void attachToProduct(UUID shopOwnerId, UUID productShopId, UUID productId,
                                UUID mediaId, int sortOrder, boolean isCover) {
        // Validate media is owned by the product's shop
        MediaAsset asset = mediaAssetRepository.findByIdAndOwnerId(mediaId, productShopId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_OWNERSHIP_VIOLATION));

        if (asset.getStatus() == MediaStatus.DELETED) {
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }
        if (asset.getPurpose() != MediaPurpose.PRODUCT_IMAGE) {
            throw new AppException(ErrorCode.MEDIA_OWNERSHIP_VIOLATION);
        }

        if (isCover) {
            productMediaRepository.clearCoverByProductId(productId);
        }

        Optional<ProductMedia> existing = productMediaRepository.findByIdProductIdAndIdMediaId(productId, mediaId);
        if (existing.isPresent()) {
            ProductMedia pm = existing.get();
            pm.setCover(isCover);
            pm.setSortOrder(sortOrder);
            productMediaRepository.save(pm);
        } else {
            ProductMedia pm = ProductMedia.builder()
                    .id(new ProductMediaId(productId, mediaId))
                    .sortOrder(sortOrder)
                    .cover(isCover)
                    .build();
            productMediaRepository.save(pm);
        }
    }

    @Override
    @Transactional
    public void replaceProductMedia(UUID shopOwnerId, UUID productShopId, UUID productId, List<UUID> mediaIds) {
        productMediaRepository.deleteAllByProductId(productId);
        List<UUID> uniqueMediaIds = new java.util.ArrayList<>(new LinkedHashSet<>(mediaIds));
        for (int i = 0; i < uniqueMediaIds.size(); i++) {
            attachToProduct(shopOwnerId, productShopId, productId, uniqueMediaIds.get(i), i, i == 0);
        }
    }

    @Override
    @Transactional
    public void detachFromProduct(UUID shopOwnerId, UUID productShopId, UUID productId, UUID mediaId) {
        // Validate ownership before detach
        mediaAssetRepository.findByIdAndOwnerId(mediaId, productShopId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_OWNERSHIP_VIOLATION));
        productMediaRepository.deleteByProductIdAndMediaId(productId, mediaId);
    }

    // ===================== Private helpers =====================

    private void validateFileSize(byte[] bytes) {
        if (bytes.length > maxImageSizeBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (bytes.length == 0) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length < 12) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (startsWith(bytes, MAGIC_JPEG)) {
            return JPEG_CONTENT_TYPE;
        }
        if (startsWith(bytes, MAGIC_PNG)) {
            return PNG_CONTENT_TYPE;
        }
        if (startsWith(bytes, MAGIC_RIFF) && matchesAt(bytes, 8, MAGIC_WEBP)) {
            return WEBP_CONTENT_TYPE;
        }
        throw new AppException(ErrorCode.INVALID_FILE_TYPE);
    }

    private String validateExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        return ext;
    }

    private void validateExtensionMatchesContentType(String extension, String contentType) {
        boolean matches = switch (contentType) {
            case JPEG_CONTENT_TYPE -> extension.equals("jpg") || extension.equals("jpeg");
            case PNG_CONTENT_TYPE -> extension.equals("png");
            case WEBP_CONTENT_TYPE -> extension.equals("webp");
            default -> false;
        };
        if (!matches) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAt(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private List<ProductMediaSummary> buildSummaries(List<ProductMedia> list) {
        List<UUID> mediaIds = list.stream().map(pm -> pm.getId().getMediaId()).toList();
        Map<UUID, MediaAsset> assetMap = mediaAssetRepository.findAllById(mediaIds).stream()
                .filter(asset -> asset.getStatus() == MediaStatus.READY)
                .collect(Collectors.toMap(MediaAsset::getId, a -> a));

        return list.stream()
                .filter(pm -> assetMap.containsKey(pm.getId().getMediaId()))
                .map(pm -> {
                    UUID mediaId = pm.getId().getMediaId();
                    MediaAsset asset = assetMap.get(mediaId);
                    String publicUrl = storageService.getPublicUrl(asset.getObjectKey());
                    return ProductMediaSummary.builder()
                            .mediaId(mediaId)
                            .publicUrl(publicUrl)
                            .objectKey(asset.getObjectKey())
                            .contentType(asset.getContentType())
                            .sortOrder(pm.getSortOrder())
                            .cover(pm.isCover())
                            .build();
                })
                .toList();
    }
}
