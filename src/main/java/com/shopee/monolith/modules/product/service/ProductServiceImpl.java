package com.shopee.monolith.modules.product.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.PagedResponse;
import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.dto.request.CreateProductRequest;
import com.shopee.monolith.modules.product.dto.request.CreateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.response.CategoryResponse;
import com.shopee.monolith.modules.product.dto.response.ProductResponse;
import com.shopee.monolith.modules.product.dto.response.ProductVariantResponse;
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
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ShopService shopService;
    private final ApplicationEventPublisher eventPublisher;

    private ShopLookupData validateShopOwner(UUID ownerId, UUID shopId) {
        ShopLookupData shop = shopService.findShopLookupDataById(shopId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!shop.ownerId().equals(ownerId)) {
            throw new AppException(ErrorCode.SHOP_OWNER_REQUIRED);
        }
        return shop;
    }

    @Override
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public ProductResponse getProductById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ProductVariantResponse> variants = productVariantRepository.findAllByProductId(productId).stream()
                .map(productMapper::toResponse)
                .toList();
        return productMapper.toResponse(product, variants);
    }

    private PagedResponse<ProductResponse> toPagedResponse(Page<Product> productPage) {
        List<Product> products = productPage.getContent();
        if (products.isEmpty()) {
            return PagedResponse.from(productPage, Collections.emptyList());
        }

        List<UUID> productIds = products.stream()
                .map(Product::getId)
                .toList();

        List<ProductVariant> allVariants = productVariantRepository.findAllByProductIdIn(productIds);

        Map<UUID, List<ProductVariantResponse>> variantsByProductId = allVariants.stream()
                .map(productMapper::toResponse)
                .collect(Collectors.groupingBy(ProductVariantResponse::productId));

        List<ProductResponse> productResponses = products.stream()
                .map(product -> {
                    List<ProductVariantResponse> productVariants = variantsByProductId.getOrDefault(product.getId(), Collections.emptyList());
                    return productMapper.toResponse(product, productVariants);
                })
                .toList();

        return PagedResponse.from(productPage, productResponses);
    }

    @Override
    public PagedResponse<ProductResponse> listProducts(int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        int boundedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> productPage = productRepository.findAll(pageable);
        return toPagedResponse(productPage);
    }

    @Override
    public PagedResponse<ProductResponse> listProductsByShop(UUID shopId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        int boundedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> productPage = productRepository.findAllByShopId(shopId, pageable);
        return toPagedResponse(productPage);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(UUID ownerId, CreateProductRequest request) {
        validateShopOwner(ownerId, request.shopId());

        if (request.categoryId() != null && !categoryRepository.existsById(request.categoryId())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        Product product = Product.builder()
                .shopId(request.shopId())
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .build();

        product = productRepository.save(product);

        eventPublisher.publishEvent(new ProductCreatedEvent(product.getId(), product.getShopId()));

        return productMapper.toResponse(product, List.of());
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID ownerId, UUID productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateShopOwner(ownerId, product.getShopId());

        if (request.categoryId() != null && !categoryRepository.existsById(request.categoryId())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        product.update(request.categoryId(), request.name(), request.description());
        product = productRepository.save(product);

        eventPublisher.publishEvent(new ProductUpdatedEvent(product.getId(), product.getShopId()));

        List<ProductVariantResponse> variants = productVariantRepository.findAllByProductId(productId).stream()
                .map(productMapper::toResponse)
                .toList();

        return productMapper.toResponse(product, variants);
    }

    @Override
    @Transactional
    public ProductVariantResponse createVariant(UUID ownerId, UUID productId, CreateProductVariantRequest request) {
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_PRICE);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateShopOwner(ownerId, product.getShopId());

        if (productVariantRepository.existsBySku(request.sku())) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        ProductVariant variant = ProductVariant.builder()
                .productId(productId)
                .sku(request.sku())
                .name(request.name())
                .price(request.price())
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

        variant.update(request.sku(), request.name(), request.price());

        try {
            variant = productVariantRepository.saveAndFlush(variant);
            return productMapper.toResponse(variant);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }
    }

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
}
