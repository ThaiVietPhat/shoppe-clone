package com.shopee.monolith.modules.product.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.media.dto.response.ProductMediaSummary;
import com.shopee.monolith.modules.media.service.MediaService;
import com.shopee.monolith.modules.product.dto.internal.ProductStockSummaryDto;
import com.shopee.monolith.modules.product.dto.request.CreateProductRequest;
import com.shopee.monolith.modules.product.dto.request.CreateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.dto.response.CategoryResponse;
import com.shopee.monolith.modules.product.dto.response.ProductCardResponse;
import com.shopee.monolith.modules.product.dto.response.ProductResponse;
import com.shopee.monolith.modules.product.dto.response.ProductDetailResponse;
import com.shopee.monolith.modules.product.dto.response.ProductEligibilityIssue;
import com.shopee.monolith.modules.product.dto.response.ProductVariantResponse;
import com.shopee.monolith.modules.product.entity.Category;
import com.shopee.monolith.modules.product.entity.Product;
import com.shopee.monolith.modules.product.entity.ProductStatus;
import com.shopee.monolith.modules.product.entity.ProductVariant;
import com.shopee.monolith.modules.product.event.ProductCatalogSnapshotEvent;
import com.shopee.monolith.modules.product.event.ProductCreatedEvent;
import com.shopee.monolith.modules.product.event.ProductUpdatedEvent;
import com.shopee.monolith.modules.product.mapper.ProductMapper;
import com.shopee.monolith.modules.product.repository.CategoryRepository;
import com.shopee.monolith.modules.product.repository.ProductRepository;
import com.shopee.monolith.modules.product.repository.ProductVariantRepository;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ShopService shopService;

    @Mock
    private ProductStockSummaryProvider stockSummaryProvider;

    @Mock
    private MediaService mediaService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductServiceImpl productService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID shopId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    private ShopLookupData shopLookup;
    private Category category;
    private CategoryResponse categoryResponse;
    private Product product;
    private ProductResponse productResponse;
    private ProductVariant variant;
    private ProductVariantResponse variantResponse;

    @BeforeEach
    void setUp() {
        shopLookup = ShopLookupData.builder()
                .id(shopId)
                .ownerId(ownerId)
                .name("Seller Shop")
                .build();

        category = Category.builder()
                .id(categoryId)
                .name("Electronics")
                .path("Electronics")
                .build();

        categoryResponse = CategoryResponse.builder()
                .id(categoryId)
                .name("Electronics")
                .build();

        product = Product.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .name("iPhone 15")
                .description("Titanium")
                .brand("Apple")
                .attributes(Map.of("storage", "256GB"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        variant = ProductVariant.builder()
                .id(variantId)
                .productId(productId)
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        variantResponse = ProductVariantResponse.builder()
                .id(variantId)
                .productId(productId)
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .build();

        productResponse = ProductResponse.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .name("iPhone 15")
                .description("Titanium")
                .variants(List.of(variantResponse))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        lenient().when(mediaService.listProductMedia(any())).thenReturn(List.of());
    }

    @Test
    void listCategoriesShouldReturnList() {
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(productMapper.toResponse(category)).thenReturn(categoryResponse);

        List<CategoryResponse> result = productService.listCategories();

        assertEquals(1, result.size());
        assertEquals(categoryResponse, result.get(0));
    }

    @Test
    void getProductByIdWhenExistsShouldReturnProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(variant));
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);
        when(productMapper.toResponse(product, List.of(variantResponse))).thenReturn(productResponse);

        ProductResponse result = productService.getProductById(productId);

        assertEquals(productResponse, result);
    }

    @Test
    void getProductByIdWhenDoesNotExistShouldThrowException() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> productService.getProductById(productId));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createProductWhenValidShouldSucceedAndPublishEvent() {
        CreateProductRequest req = CreateProductRequest.builder()
                .shopId(shopId)
                .categoryId(categoryId)
                .name("iPhone 15")
                .description("Titanium")
                .brand("Apple")
                .attributes(Map.of("storage", "256GB"))
                .build();

        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(any(Product.class), any())).thenReturn(productResponse);

        ProductResponse result = productService.createProduct(ownerId, req);

        assertEquals(productResponse, result);
        verify(eventPublisher).publishEvent(any(ProductCreatedEvent.class));
        ArgumentCaptor<ProductCatalogSnapshotEvent> snapshotCaptor =
                ArgumentCaptor.forClass(ProductCatalogSnapshotEvent.class);
        verify(eventPublisher).publishEvent(snapshotCaptor.capture());
        ProductCatalogSnapshotEvent snapshot = snapshotCaptor.getValue();
        assertEquals(productId, snapshot.productId());
        assertEquals(shopId, snapshot.shopId());
        assertEquals("Electronics", snapshot.categoryPath());
        assertEquals("Apple", snapshot.brand());
        assertEquals(Map.of("storage", "256GB"), snapshot.attributes());
    }

    @Test
    void createProductWhenCategoryNotFoundShouldThrowException() {
        CreateProductRequest req = CreateProductRequest.builder()
                .shopId(shopId)
                .categoryId(categoryId)
                .name("iPhone 15")
                .build();

        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> productService.createProduct(ownerId, req));

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProductWhenShopOwnerMismatchShouldThrowException() {
        CreateProductRequest req = CreateProductRequest.builder()
                .shopId(shopId)
                .name("iPhone 15")
                .build();

        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));

        AppException ex = assertThrows(AppException.class, () -> productService.createProduct(UUID.randomUUID(), req));

        assertEquals(ErrorCode.SHOP_OWNER_REQUIRED, ex.getErrorCode());
    }

    @Test
    void updateProductWhenValidShouldSucceedAndPublishEvent() {
        UpdateProductRequest req = UpdateProductRequest.builder()
                .categoryId(categoryId)
                .name("iPhone 15 Updated")
                .description("Desc")
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(productRepository.save(product)).thenReturn(product);
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(variant));
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);
        when(productMapper.toResponse(product, List.of(variantResponse))).thenReturn(productResponse);

        ProductResponse result = productService.updateProduct(ownerId, productId, req);

        assertEquals(productResponse, result);
        verify(eventPublisher).publishEvent(any(ProductUpdatedEvent.class));
    }

    @Test
    void createVariantWhenValidShouldSucceed() {
        CreateProductVariantRequest req = CreateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(productVariantRepository.existsBySku("IPHONE-15-256")).thenReturn(false);
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);
        when(productVariantRepository.findMinPriceByProductId(productId)).thenReturn(Optional.of(BigDecimal.valueOf(999.00)));
        when(productVariantRepository.findMaxPriceByProductId(productId)).thenReturn(Optional.of(BigDecimal.valueOf(999.00)));
        when(productRepository.save(product)).thenReturn(product);
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(variant));
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);

        ProductVariantResponse result = productService.createVariant(ownerId, productId, req);

        assertEquals(variantResponse, result);
        verify(eventPublisher).publishEvent(any(ProductUpdatedEvent.class));
        verify(eventPublisher).publishEvent(any(ProductCatalogSnapshotEvent.class));
    }

    @Test
    void createVariantWhenProductActiveShouldRecomputePriceRangeAndPublishSnapshot() {
        Product activeProduct = Product.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .name("iPhone 15")
                .status(ProductStatus.ACTIVE)
                .build();
        CreateProductVariantRequest req = CreateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(activeProduct));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(productVariantRepository.existsBySku("IPHONE-15-256")).thenReturn(false);
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);
        when(productVariantRepository.findMinPriceByProductId(productId)).thenReturn(Optional.of(BigDecimal.valueOf(999.00)));
        when(productVariantRepository.findMaxPriceByProductId(productId)).thenReturn(Optional.of(BigDecimal.valueOf(999.00)));
        when(productVariantRepository.countActiveVariantsWithPriceAbove(productId, BigDecimal.ZERO)).thenReturn(1L);
        when(productRepository.save(activeProduct)).thenReturn(activeProduct);
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(variant));
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);

        ProductVariantResponse result = productService.createVariant(ownerId, productId, req);

        assertEquals(variantResponse, result);
        assertEquals(BigDecimal.valueOf(999.00), activeProduct.getMinPrice());
        assertEquals(BigDecimal.valueOf(999.00), activeProduct.getMaxPrice());
        verify(eventPublisher).publishEvent(any(ProductUpdatedEvent.class));
        verify(eventPublisher).publishEvent(any(ProductCatalogSnapshotEvent.class));
    }

    @Test
    void createVariantWhenPriceNegativeShouldThrowException() {
        CreateProductVariantRequest req = CreateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(-1.00))
                .build();

        AppException ex = assertThrows(AppException.class, () -> productService.createVariant(ownerId, productId, req));

        assertEquals(ErrorCode.INVALID_PRODUCT_PRICE, ex.getErrorCode());
    }

    @Test
    void createVariantWhenSkuExistsShouldThrowException() {
        CreateProductVariantRequest req = CreateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(productVariantRepository.existsBySku("IPHONE-15-256")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> productService.createVariant(ownerId, productId, req));

        assertEquals(ErrorCode.SKU_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void updateVariantWhenSkuExistsOnAnotherVariantShouldThrowException() {
        UpdateProductVariantRequest req = UpdateProductVariantRequest.builder()
                .sku("IPHONE-15-DUPLICATE")
                .name("Updated variant")
                .price(BigDecimal.valueOf(1099.00))
                .build();

        ProductVariant anotherVariant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .sku("IPHONE-15-DUPLICATE")
                .build();

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(productVariantRepository.findBySku("IPHONE-15-DUPLICATE")).thenReturn(Optional.of(anotherVariant));

        AppException ex = assertThrows(AppException.class, () -> productService.updateVariant(ownerId, productId, variantId, req));

        assertEquals(ErrorCode.SKU_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void updateVariantWhenActiveOmittedShouldPreserveCurrentActiveFlag() {
        UpdateProductVariantRequest req = UpdateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("Updated variant")
                .price(BigDecimal.valueOf(1099.00))
                .active(null)
                .build();
        ProductVariant inactiveVariant = ProductVariant.builder()
                .id(variantId)
                .productId(productId)
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .active(false)
                .build();

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(inactiveVariant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(productVariantRepository.findBySku("IPHONE-15-256")).thenReturn(Optional.of(inactiveVariant));
        when(productVariantRepository.saveAndFlush(inactiveVariant)).thenReturn(inactiveVariant);
        when(productVariantRepository.findMinPriceByProductId(productId)).thenReturn(Optional.empty());
        when(productVariantRepository.findMaxPriceByProductId(productId)).thenReturn(Optional.empty());
        when(productRepository.save(product)).thenReturn(product);
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(inactiveVariant));
        when(productMapper.toResponse(inactiveVariant)).thenReturn(variantResponse);

        productService.updateVariant(ownerId, productId, variantId, req);

        ArgumentCaptor<ProductVariant> variantCaptor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).saveAndFlush(variantCaptor.capture());
        assertEquals(false, variantCaptor.getValue().isActive());
    }

    @Test
    void updateVariantWhenProductDeletedShouldThrowException() {
        UpdateProductVariantRequest req = UpdateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("Updated variant")
                .price(BigDecimal.valueOf(1099.00))
                .build();
        Product deletedProduct = Product.builder()
                .id(productId)
                .shopId(shopId)
                .status(ProductStatus.DELETED)
                .build();

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(deletedProduct));

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateVariant(ownerId, productId, variantId, req));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verify(productVariantRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateVariantWhenNotBelongToProductShouldThrowException() {
        UpdateProductVariantRequest req = UpdateProductVariantRequest.builder()
                .sku("IPHONE-15-256")
                .name("256GB Black")
                .price(BigDecimal.valueOf(999.00))
                .build();

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        AppException ex = assertThrows(AppException.class, () -> productService.updateVariant(ownerId, UUID.randomUUID(), variantId, req));

        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void listProductsShouldReturnPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findAll(pageable)).thenReturn(page);
        when(productVariantRepository.findAllByProductIdIn(List.of(productId))).thenReturn(List.of(variant));
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);
        when(productMapper.toResponse(product, List.of(variantResponse))).thenReturn(productResponse);

        PagedResponse<ProductResponse> result = productService.listProducts(0, 20);

        assertEquals(1, result.items().size());
        assertEquals(productResponse, result.items().get(0));
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.totalElements());
    }

    @Test
    void listProductsByShopShouldReturnPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findAllByShopId(shopId, pageable)).thenReturn(page);
        when(productVariantRepository.findAllByProductIdIn(List.of(productId))).thenReturn(List.of(variant));
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);
        when(productMapper.toResponse(product, List.of(variantResponse))).thenReturn(productResponse);

        PagedResponse<ProductResponse> result = productService.listProductsByShop(shopId, 0, 20);

        assertEquals(1, result.items().size());
        assertEquals(productResponse, result.items().get(0));
    }

    @Test
    void listActiveProductsShouldReturnCardMetadataAndEligibility() {
        Product activeProduct = Product.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .status(ProductStatus.ACTIVE)
                .name("iPhone 15")
                .brand("Apple")
                .minPrice(BigDecimal.valueOf(999.00))
                .maxPrice(BigDecimal.valueOf(1299.00))
                .createdAt(Instant.now())
                .build();
        ProductMediaSummary cover = ProductMediaSummary.builder()
                .mediaId(UUID.randomUUID())
                .publicUrl("http://localhost/media/cover.png")
                .objectKey("cover.png")
                .contentType("image/png")
                .cover(true)
                .build();
        ShopLookupData ratedShop = ShopLookupData.builder()
                .id(shopId)
                .ownerId(ownerId)
                .name("Seller Shop")
                .rating(BigDecimal.valueOf(4.85))
                .build();
        Pageable pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> page = new PageImpl<>(List.of(activeProduct), pageable, 1);

        when(productRepository.findAllByStatus(ProductStatus.ACTIVE, pageable)).thenReturn(page);
        when(mediaService.listProductMediaByProductIds(List.of(productId))).thenReturn(Map.of(productId, List.of(cover)));
        when(productVariantRepository.findAllByProductIdIn(List.of(productId))).thenReturn(List.of(variant));
        when(stockSummaryProvider.getStockSummariesByVariantIds(List.of(variantId))).thenReturn(Map.of(
                variantId,
                ProductStockSummaryDto.builder()
                        .variantId(variantId)
                        .availableStock(10)
                        .reservedStock(0)
                        .build()
        ));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(ratedShop));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        PagedResponse<ProductCardResponse> result = productService.listActiveProducts(0, 20);
        ProductCardResponse card = result.items().get(0);

        assertEquals(cover.mediaId(), card.coverMediaId());
        assertEquals("image/png", card.coverContentType());
        assertEquals(BigDecimal.valueOf(4.85), card.shopRating());
        assertTrue(card.checkoutEligible());
        assertTrue(card.eligibilityIssues().isEmpty());
    }

    @Test
    void listActiveProductsShouldExposeCardEligibilityIssuesWhenNoStock() {
        Product activeProduct = Product.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .status(ProductStatus.ACTIVE)
                .name("iPhone 15")
                .build();
        Pageable pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> page = new PageImpl<>(List.of(activeProduct), pageable, 1);

        when(productRepository.findAllByStatus(ProductStatus.ACTIVE, pageable)).thenReturn(page);
        when(mediaService.listProductMediaByProductIds(List.of(productId))).thenReturn(Map.of());
        when(productVariantRepository.findAllByProductIdIn(List.of(productId))).thenReturn(List.of(variant));
        when(stockSummaryProvider.getStockSummariesByVariantIds(List.of(variantId))).thenReturn(Map.of());
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        PagedResponse<ProductCardResponse> result = productService.listActiveProducts(0, 20);
        ProductCardResponse card = result.items().get(0);

        assertFalse(card.checkoutEligible());
        assertTrue(card.eligibilityIssues().contains(ProductEligibilityIssue.NO_STOCK));
    }

    @Test
    void listProductsWithInvalidParamsShouldThrowException() {
        AppException ex1 = assertThrows(AppException.class, () -> productService.listProducts(-1, 20));
        assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () -> productService.listProducts(0, 0));
        assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
    }

    @Test
    void listProductsByShopWithInvalidParamsShouldThrowException() {
        AppException ex1 = assertThrows(AppException.class, () -> productService.listProductsByShop(shopId, -1, 20));
        assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () -> productService.listProductsByShop(shopId, 0, 0));
        assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
    }

    @Test
    void findActiveProductLookupDataByIdWhenProductIsNotActiveShouldReturnEmpty() {
        when(productRepository.findByIdAndStatus(productId, ProductStatus.ACTIVE)).thenReturn(Optional.empty());

        Optional<ProductLookupData> result = productService.findActiveProductLookupDataById(productId);

        assertEquals(Optional.empty(), result);
    }

    @Test
    void findActiveVariantLookupDataByIdWhenVariantOrProductIsNotActiveShouldReturnEmpty() {
        when(productVariantRepository.findActiveByIdAndProductStatus(variantId, ProductStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Optional<VariantLookupData> result = productService.findActiveVariantLookupDataById(variantId);

        assertEquals(Optional.empty(), result);
    }

    @Test
    void getProductDetailForSellerShouldExposeEligibilityIssues() {
        Product draftProduct = Product.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .status(ProductStatus.DRAFT)
                .name("Draft product")
                .build();
        ProductVariant zeroPriceVariant = ProductVariant.builder()
                .id(variantId)
                .productId(productId)
                .sku("ZERO-PRICE")
                .name("Zero price")
                .price(BigDecimal.ZERO)
                .active(true)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(draftProduct));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(zeroPriceVariant));
        when(stockSummaryProvider.getStockSummariesByVariantIds(List.of(variantId))).thenReturn(Map.of());

        ProductDetailResponse result = productService.getProductDetailForSeller(ownerId, productId);

        assertTrue(result.eligibilityIssues().contains(ProductEligibilityIssue.PRODUCT_NOT_ACTIVE));
        assertTrue(result.eligibilityIssues().contains(ProductEligibilityIssue.NO_POSITIVE_PRICE));
        assertTrue(result.eligibilityIssues().contains(ProductEligibilityIssue.NO_STOCK));
    }

    @Test
    void getProductDetailForSellerShouldIncludeShopRating() {
        Product activeProduct = Product.builder()
                .id(productId)
                .shopId(shopId)
                .categoryId(categoryId)
                .status(ProductStatus.ACTIVE)
                .name("Active product")
                .build();
        ShopLookupData ratedShop = ShopLookupData.builder()
                .id(shopId)
                .ownerId(ownerId)
                .name("Seller Shop")
                .rating(BigDecimal.valueOf(4.85))
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(activeProduct));
        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(ratedShop));
        when(productVariantRepository.findAllByProductId(productId)).thenReturn(List.of(variant));
        when(stockSummaryProvider.getStockSummariesByVariantIds(List.of(variantId))).thenReturn(Map.of());

        ProductDetailResponse result = productService.getProductDetailForSeller(ownerId, productId);

        assertEquals(BigDecimal.valueOf(4.85), result.shop().rating());
    }
}
