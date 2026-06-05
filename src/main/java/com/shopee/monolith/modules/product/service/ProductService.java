package com.shopee.monolith.modules.product.service;

import com.shopee.monolith.modules.product.dto.internal.ProductLookupData;
import com.shopee.monolith.modules.product.dto.internal.VariantLookupData;
import com.shopee.monolith.modules.product.dto.request.CreateProductRequest;
import com.shopee.monolith.modules.product.dto.request.CreateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductRequest;
import com.shopee.monolith.modules.product.dto.request.UpdateProductVariantRequest;
import com.shopee.monolith.modules.product.dto.response.CategoryResponse;
import com.shopee.monolith.modules.product.dto.response.ProductResponse;
import com.shopee.monolith.modules.product.dto.response.ProductVariantResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductService {

    List<CategoryResponse> listCategories();

    ProductResponse getProductById(UUID productId);

    List<ProductResponse> listProducts();

    List<ProductResponse> listProductsByShop(UUID shopId);

    ProductResponse createProduct(UUID ownerId, CreateProductRequest request);

    ProductResponse updateProduct(UUID ownerId, UUID productId, UpdateProductRequest request);

    ProductVariantResponse createVariant(UUID ownerId, UUID productId, CreateProductVariantRequest request);

    ProductVariantResponse updateVariant(UUID ownerId, UUID productId, UUID variantId, UpdateProductVariantRequest request);

    Optional<ProductLookupData> findProductLookupDataById(UUID productId);

    Optional<VariantLookupData> findVariantLookupDataById(UUID variantId);
}
