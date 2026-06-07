package com.shopee.monolith.modules.product.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.media.dto.response.ProductMediaSummary;
import com.shopee.monolith.modules.media.service.MediaService;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.ProductStockSummaryDto;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.dto.request.CreateProductRequest;
import com.shopee.monolith.modules.product.dto.request.CreateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.response.CategoryResponse;
import com.shopee.monolith.modules.product.dto.response.ProductCardResponse;
import com.shopee.monolith.modules.product.dto.response.ProductDetailResponse;
import com.shopee.monolith.modules.product.dto.response.ProductResponse;
import com.shopee.monolith.modules.product.dto.response.ProductVariantDetailResponse;
import com.shopee.monolith.modules.product.dto.response.ProductVariantResponse;
import com.shopee.monolith.modules.product.dto.response.ShopSummaryDto;
import com.shopee.monolith.modules.product.entity.Category;
import com.shopee.monolith.modules.product.entity.Product;
import com.shopee.monolith.modules.product.entity.ProductStatus;
import com.shopee.monolith.modules.product.entity.ProductVariant;
import com.shopee.monolith.modules.product.event.ProductCatalogSnapshotEvent;
import com.shopee.monolith.modules.product.event.ProductCreatedEvent;
import com.shopee.monolith.modules.product.event.ProductListingStatusChangedEvent;
import com.shopee.monolith.modules.product.event.ProductUpdatedEvent;
import com.shopee.monolith.modules.product.mapper.ProductMapper;
import com.shopee.monolith.modules.product.repository.CategoryRepository;
import com.shopee.monolith.modules.product.repository.ProductRepository;
import com.shopee.monolith.modules.product.repository.ProductVariantRepository;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ShopService shopService;
    private final ProductStockSummaryProvider stockSummaryProvider;
    private final MediaService mediaService;
    private final ApplicationEventPublisher eventPublisher;

    // ===================== Validators =====================

    private ShopLookupData validateShopOwner(UUID ownerId, UUID shopId) {
        ShopLookupData shop = shopService.findShopLookupDataById(shopId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!shop.ownerId().equals(ownerId)) {
            throw new AppException(ErrorCode.SHOP_OWNER_REQUIRED);
        }
        return shop;
    }

    private void validateCategory(UUID categoryId) {
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private String resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .map(Category::getPath)
                .orElse(null);
    }

    private static Pageable buildPageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ===================== Category =====================

    @Override
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    // ===================== Public read (ACTIVE only) =====================

    @Override
    public ProductDetailResponse getProductDetailById(UUID productId) {
        Product product = productRepository.findByIdAndStatus(productId, ProductStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return buildProductDetail(product, false);
    }

    @Override
    public PagedResponse<ProductCardResponse> listActiveProducts(int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Page<Product> productPage = productRepository.findAllByStatus(ProductStatus.ACTIVE, buildPageable(page, size));
        return toCardPagedResponse(productPage);
    }

    @Override
    public PagedResponse<ProductCardResponse> listActiveProductsByShop(UUID shopId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Page<Product> productPage = productRepository.findAllByShopIdAndStatus(shopId, ProductStatus.ACTIVE, buildPageable(page, size));
        return toCardPagedResponse(productPage);
    }

    // ===================== Legacy public read (backward compat) =====================

    @Override
    public ProductResponse getProductById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ProductVariantResponse> variants = productVariantRepository.findAllByProductId(productId).stream()
                .map(productMapper::toResponse)
                .toList();
        return productMapper.toResponse(product, variants);
    }

    @Override
    public PagedResponse<ProductResponse> listProducts(int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Page<Product> productPage = productRepository.findAll(buildPageable(page, size));
        return toPagedResponse(productPage);
    }

    @Override
    public PagedResponse<ProductResponse> listProductsByShop(UUID shopId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Page<Product> productPage = productRepository.findAllByShopId(shopId, buildPageable(page, size));
        return toPagedResponse(productPage);
    }

    // ===================== Seller create/update =====================

    @Override
    @Transactional
    public ProductResponse createProduct(UUID ownerId, CreateProductRequest request) {
        validateShopOwner(ownerId, request.shopId());
        validateCategory(request.categoryId());

        Product product = Product.builder()
                .shopId(request.shopId())
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .status(ProductStatus.DRAFT)
                .brand(request.brand())
                .sellerSku(request.sellerSku())
                .attributes(request.attributes())
                .build();

        product = productRepository.save(product);

        // Attach media if provided
        if (request.mediaIds() != null) {
            replaceProductMedia(ownerId, request.shopId(), product.getId(), request.mediaIds());
        }

        eventPublisher.publishEvent(new ProductCreatedEvent(product.getId(), product.getShopId()));
        publishCatalogSnapshot(product, List.of());

        return productMapper.toResponse(product, List.of());
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID ownerId, UUID productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() == ProductStatus.DELETED) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        validateShopOwner(ownerId, product.getShopId());
        validateCategory(request.categoryId());

        product.update(request.categoryId(), request.name(), request.description(),
                request.brand(), request.sellerSku(), request.attributes());
        product = productRepository.save(product);

        // Reattach media if provided
        if (request.mediaIds() != null) {
            replaceProductMedia(ownerId, product.getShopId(), product.getId(), request.mediaIds());
        }

        List<ProductVariant> variants = productVariantRepository.findAllByProductId(productId);
        eventPublisher.publishEvent(new ProductUpdatedEvent(product.getId(), product.getShopId()));
        publishCatalogSnapshot(product, variants);

        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(productMapper::toResponse)
                .toList();
        return productMapper.toResponse(product, variantResponses);
    }

    // ===================== Seller variant =====================

    @Override
    @Transactional
    public ProductVariantResponse createVariant(UUID ownerId, UUID productId, CreateProductVariantRequest request) {
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_PRICE);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() == ProductStatus.DELETED) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        validateShopOwner(ownerId, product.getShopId());

        if (productVariantRepository.existsBySku(request.sku())) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        ProductVariant variant = ProductVariant.builder()
                .productId(productId)
                .sku(request.sku())
                .name(request.name())
                .price(request.price())
                .optionLabels(request.optionLabels())
                .active(request.isActiveOrDefault())
                .build();

        try {
            variant = productVariantRepository.saveAndFlush(variant);
            return productMapper.toResponse(variant);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional
    public ProductVariantResponse updateVariant(UUID ownerId, UUID productId, UUID variantId, UpdateProductVariantRequest request) {
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_PRICE);
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

        if (!variant.getProductId().equals(productId)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateShopOwner(ownerId, product.getShopId());

        Optional<ProductVariant> existingSku = productVariantRepository.findBySku(request.sku());
        if (existingSku.isPresent() && !existingSku.get().getId().equals(variantId)) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        boolean activeFlag = request.active() == null || request.active();
        variant.update(request.sku(), request.name(), request.price(), request.optionLabels(), activeFlag);

        try {
            variant = productVariantRepository.saveAndFlush(variant);
            return productMapper.toResponse(variant);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }
    }

    // ===================== Seller lifecycle =====================

    @Override
    @Transactional
    public ProductDetailResponse publishProduct(UUID ownerId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateShopOwner(ownerId, product.getShopId());

        if (!product.isPublishable()) {
            throw new AppException(ErrorCode.PRODUCT_CANNOT_BE_PUBLISHED);
        }

        // Must have at least one active variant with price > 0
        long eligibleVariants = productVariantRepository.countActiveVariantsWithPriceAbove(productId, BigDecimal.ZERO);
        if (eligibleVariants == 0) {
            throw new AppException(ErrorCode.PRODUCT_HAS_NO_ACTIVE_VARIANT);
        }

        // Recompute price range from active variants
        recomputeProductPriceRange(product);
        product.publish();
        product = productRepository.save(product);

        List<ProductVariant> variants = productVariantRepository.findAllByProductId(productId);

        eventPublisher.publishEvent(new ProductListingStatusChangedEvent(
                product.getId(), product.getShopId(), ProductStatus.ACTIVE));
        publishCatalogSnapshot(product, variants);

        return buildProductDetail(product, true);
    }

    @Override
    @Transactional
    public ProductDetailResponse unpublishProduct(UUID ownerId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateShopOwner(ownerId, product.getShopId());
        product.unpublish();
        product = productRepository.save(product);

        List<ProductVariant> variants = productVariantRepository.findAllByProductId(productId);

        eventPublisher.publishEvent(new ProductListingStatusChangedEvent(
                product.getId(), product.getShopId(), ProductStatus.INACTIVE));
        publishCatalogSnapshot(product, variants);

        return buildProductDetail(product, true);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID ownerId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateShopOwner(ownerId, product.getShopId());

        if (product.getStatus() == ProductStatus.DELETED) {
            return; // Idempotent
        }

        product.softDelete();
        productRepository.save(product);

        eventPublisher.publishEvent(new ProductListingStatusChangedEvent(
                product.getId(), product.getShopId(), ProductStatus.DELETED));
        List<ProductVariant> variants = productVariantRepository.findAllByProductId(productId);
        publishCatalogSnapshot(product, variants);
    }

    @Override
    public PagedResponse<ProductDetailResponse> listSellerProducts(UUID ownerId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        ShopLookupData shop = shopService.findShopLookupDataByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        Page<Product> productPage = productRepository.findAllByShopIdAndStatusNot(
                shop.id(), ProductStatus.DELETED, buildPageable(page, size));

        List<ProductDetailResponse> content = productPage.getContent().stream()
                .map(p -> buildProductDetail(p, true))
                .toList();

        return PagedResponse.from(productPage, content);
    }

    @Override
    public ProductDetailResponse getProductDetailForSeller(UUID ownerId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() == ProductStatus.DELETED) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        validateShopOwner(ownerId, product.getShopId());
        return buildProductDetail(product, true);
    }

    // ===================== Cross-module internal lookups =====================

    @Override
    public Optional<ProductLookupData> findProductLookupDataById(UUID productId) {
        return productRepository.findById(productId)
                .map(productMapper::toLookupData);
    }

    @Override
    public Optional<VariantLookupData> findVariantLookupDataById(UUID variantId) {
        return productVariantRepository.findById(variantId)
                .map(productMapper::toLookupData);
    }

    // ===================== Private builders =====================

    private ProductDetailResponse buildProductDetail(Product product, boolean includeInactiveVariants) {
        List<ProductVariant> variants = includeInactiveVariants
                ? productVariantRepository.findAllByProductId(product.getId())
                : productVariantRepository.findAllByProductIdAndActive(product.getId(), true);

        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, ProductStockSummaryDto> stockMap = stockSummaryProvider.getStockSummariesByVariantIds(variantIds);

        List<ProductMediaSummary> media = mediaService.listProductMedia(product.getId());
        boolean hasCover = media.stream().anyMatch(ProductMediaSummary::cover);
        ProductMediaSummary coverMedia = media.stream().filter(ProductMediaSummary::cover).findFirst().orElse(null);

        int totalStock = stockMap.values().stream().mapToInt(ProductStockSummaryDto::availableStock).sum();

        List<ProductVariantDetailResponse> variantDetails = variants.stream()
                .map(v -> {
                    ProductStockSummaryDto stock = stockMap.getOrDefault(v.getId(),
                            ProductStockSummaryDto.empty(v.getId()));
                    boolean checkoutEligible = product.getStatus() == ProductStatus.ACTIVE
                            && v.isActive()
                            && stock.availableStock() > 0;
                    return ProductVariantDetailResponse.builder()
                            .id(v.getId())
                            .productId(v.getProductId())
                            .sku(v.getSku())
                            .name(v.getName())
                            .price(v.getPrice())
                            .optionLabels(v.getOptionLabels())
                            .active(v.isActive())
                            .availableStock(stock.availableStock())
                            .checkoutEligible(checkoutEligible)
                            .coverMedia(coverMedia)
                            .createdAt(v.getCreatedAt())
                            .updatedAt(v.getUpdatedAt())
                            .build();
                })
                .toList();

        ShopLookupData shop = shopService.findShopLookupDataById(product.getShopId()).orElse(null);
        ShopSummaryDto shopSummary = shop != null
                ? ShopSummaryDto.builder().id(shop.id()).name(shop.name()).build()
                : null;

        String categoryPath = resolveCategory(product.getCategoryId());

        return ProductDetailResponse.builder()
                .id(product.getId())
                .shopId(product.getShopId())
                .status(product.getStatus())
                .name(product.getName())
                .description(product.getDescription())
                .brand(product.getBrand())
                .sellerSku(product.getSellerSku())
                .categoryId(product.getCategoryId())
                .categoryPath(categoryPath)
                .attributes(product.getAttributes())
                .minPrice(product.getMinPrice())
                .maxPrice(product.getMaxPrice())
                .hasCover(hasCover)
                .media(media)
                .variants(variantDetails)
                .shop(shopSummary)
                .totalAvailableStock(totalStock)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private void recomputeProductPriceRange(Product product) {
        BigDecimal min = productVariantRepository.findMinPriceByProductId(product.getId()).orElse(null);
        BigDecimal max = productVariantRepository.findMaxPriceByProductId(product.getId()).orElse(null);
        product.recomputePriceRange(min, max);
    }

    private void replaceProductMedia(UUID ownerId, UUID shopId, UUID productId, List<UUID> mediaIds) {
        mediaService.replaceProductMedia(ownerId, shopId, productId, mediaIds);
    }

    private void publishCatalogSnapshot(Product product, List<ProductVariant> variants) {
        List<ProductMediaSummary> media = mediaService.listProductMedia(product.getId());
        ProductMediaSummary cover = media.stream().filter(ProductMediaSummary::cover).findFirst().orElse(null);

        List<ProductCatalogSnapshotEvent.VariantSnapshot> variantSnapshots = variants.stream()
                .map(v -> new ProductCatalogSnapshotEvent.VariantSnapshot(
                        v.getId(), v.getSku(), v.getPrice(), v.getOptionLabels(), v.isActive()))
                .toList();

        eventPublisher.publishEvent(new ProductCatalogSnapshotEvent(
                product.getId(),
                product.getShopId(),
                product.getStatus(),
                product.getName(),
                product.getDescription(),
                resolveCategory(product.getCategoryId()),
                product.getBrand(),
                product.getAttributes(),
                product.getMinPrice(),
                product.getMaxPrice(),
                cover != null ? cover.mediaId() : null,
                cover != null ? cover.objectKey() : null,
                variantSnapshots
        ));
    }

    private PagedResponse<ProductResponse> toPagedResponse(Page<Product> productPage) {
        List<Product> products = productPage.getContent();
        if (products.isEmpty()) {
            return PagedResponse.from(productPage, Collections.emptyList());
        }

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        List<ProductVariant> allVariants = productVariantRepository.findAllByProductIdIn(productIds);
        Map<UUID, List<ProductVariantResponse>> variantsByProductId = allVariants.stream()
                .map(productMapper::toResponse)
                .collect(Collectors.groupingBy(ProductVariantResponse::productId));

        List<ProductResponse> productResponses = products.stream()
                .map(p -> productMapper.toResponse(p,
                        variantsByProductId.getOrDefault(p.getId(), Collections.emptyList())))
                .toList();

        return PagedResponse.from(productPage, productResponses);
    }

    private PagedResponse<ProductCardResponse> toCardPagedResponse(Page<Product> productPage) {
        List<Product> products = productPage.getContent();
        if (products.isEmpty()) {
            return PagedResponse.from(productPage, Collections.emptyList());
        }

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<ProductMediaSummary>> mediaMap = mediaService.listProductMediaByProductIds(productIds);

        List<ProductCardResponse> cards = products.stream().map(p -> {
            List<ProductMediaSummary> media = mediaMap.getOrDefault(p.getId(), List.of());
            ProductMediaSummary cover = media.stream().filter(ProductMediaSummary::cover).findFirst().orElse(null);
            ShopLookupData shop = shopService.findShopLookupDataById(p.getShopId()).orElse(null);

            return ProductCardResponse.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .brand(p.getBrand())
                    .coverImageUrl(cover != null ? cover.publicUrl() : null)
                    .coverObjectKey(cover != null ? cover.objectKey() : null)
                    .minPrice(p.getMinPrice())
                    .maxPrice(p.getMaxPrice())
                    .status(p.getStatus())
                    .shopId(p.getShopId())
                    .shopName(shop != null ? shop.name() : null)
                    .categoryPath(resolveCategory(p.getCategoryId()))
                    .createdAt(p.getCreatedAt())
                    .build();
        }).toList();

        return PagedResponse.from(productPage, cards);
    }
}
