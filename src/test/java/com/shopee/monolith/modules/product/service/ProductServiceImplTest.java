package com.shopee.monolith.modules.product.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.product.dto.request.CreateProductRequest;
import com.shopee.monolith.modules.product.dto.request.CreateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.response.CategoryResponse;
import com.shopee.monolith.modules.product.dto.response.ProductResponse;
import com.shopee.monolith.modules.product.dto.response.ProductVariantResponse;
import com.shopee.monolith.modules.product.entity.Category;
import com.shopee.monolith.modules.product.entity.Product;
import com.shopee.monolith.modules.product.entity.ProductVariant;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
                .build();

        when(shopService.findShopLookupDataById(shopId)).thenReturn(Optional.of(shopLookup));
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(any(Product.class), any())).thenReturn(productResponse);

        ProductResponse result = productService.createProduct(ownerId, req);

        assertEquals(productResponse, result);
        verify(eventPublisher).publishEvent(any(ProductCreatedEvent.class));
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
        when(productMapper.toResponse(variant)).thenReturn(variantResponse);

        ProductVariantResponse result = productService.createVariant(ownerId, productId, req);

        assertEquals(variantResponse, result);
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
}
