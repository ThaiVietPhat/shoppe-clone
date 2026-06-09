package com.shopee.monolith.modules.product.controller;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.product.entity.Category;
import com.shopee.monolith.modules.product.entity.Product;
import com.shopee.monolith.modules.product.entity.ProductStatus;
import com.shopee.monolith.modules.product.entity.ProductVariant;
import com.shopee.monolith.modules.product.repository.CategoryRepository;
import com.shopee.monolith.modules.product.repository.ProductRepository;
import com.shopee.monolith.modules.product.repository.ProductVariantRepository;
import com.shopee.monolith.modules.user.entity.Shop;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.ShopRepository;
import com.shopee.monolith.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for deterministic homepage feed and category browse endpoints.
 * Both endpoints read from PostgreSQL only — no Elasticsearch or AI dependency.
 */
@AutoConfigureMockMvc
class HomepageAndCategoryBrowseIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    private User seller;
    private Shop shop;
    private Category parentCategory;
    private Category childCategory;
    private Product activeProduct;
    private Product draftProduct;
    private Product childCategoryProduct;

    @BeforeEach
    void setUp() {
        tearDown();

        seller = userRepository.save(User.builder()
                .email("seller-browse@shoppe.local")
                .normalizedEmail("seller-browse@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build());

        shop = shopRepository.save(Shop.builder()
                .ownerId(seller.getId())
                .name("Browse Test Shop")
                .build());

        // Parent category with materialized path
        parentCategory = categoryRepository.save(Category.builder()
                .name("Electronics")
                .path("Electronics")
                .build());

        // Child category whose path starts with parentCategory.path
        childCategory = categoryRepository.save(Category.builder()
                .name("Phones")
                .parentId(parentCategory.getId())
                .path("Electronics/Phones")
                .build());

        activeProduct = productRepository.save(Product.builder()
                .shopId(shop.getId())
                .categoryId(parentCategory.getId())
                .name("Active Product")
                .status(ProductStatus.ACTIVE)
                .minPrice(BigDecimal.valueOf(100))
                .maxPrice(BigDecimal.valueOf(100))
                .build());

        productVariantRepository.save(ProductVariant.builder()
                .productId(activeProduct.getId())
                .sku("ACT-001")
                .name("Standard")
                .price(BigDecimal.valueOf(100))
                .build());

        // DRAFT product — must not appear in public endpoints
        draftProduct = productRepository.save(Product.builder()
                .shopId(shop.getId())
                .categoryId(parentCategory.getId())
                .name("Draft Product")
                .status(ProductStatus.DRAFT)
                .minPrice(BigDecimal.valueOf(50))
                .maxPrice(BigDecimal.valueOf(50))
                .build());

        childCategoryProduct = productRepository.save(Product.builder()
                .shopId(shop.getId())
                .categoryId(childCategory.getId())
                .name("Child Category Product")
                .status(ProductStatus.ACTIVE)
                .minPrice(BigDecimal.valueOf(200))
                .maxPrice(BigDecimal.valueOf(200))
                .build());

        productVariantRepository.save(ProductVariant.builder()
                .productId(childCategoryProduct.getId())
                .sku("CHILD-001")
                .name("Standard")
                .price(BigDecimal.valueOf(200))
                .build());
    }

    @AfterEach
    void tearDown() {
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ========================= Homepage =========================

    @Test
    void listHomepageProducts_whenPublic_shouldReturnOnlyActiveProducts() throws Exception {
        mockMvc.perform(get("/api/products/homepage")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listHomepageProducts_shouldExcludeDraftProducts() throws Exception {
        mockMvc.perform(get("/api/products/homepage")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        // Draft product not counted: totalElements == 2 (active + childCategory), not 3
    }

    @Test
    void listHomepageProducts_whenNoProducts_shouldReturnEmptyPage() throws Exception {
        productVariantRepository.deleteAll();
        productRepository.deleteAll();

        mockMvc.perform(get("/api/products/homepage")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void listHomepageProducts_withPagination_shouldReturnCorrectPage() throws Exception {
        mockMvc.perform(get("/api/products/homepage")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    // ========================= Category Browse =========================

    @Test
    void listProductsByCategory_whenPublicAndValidCategory_shouldReturnActiveProducts() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/products", parentCategory.getId())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listProductsByCategory_shouldIncludeSubcategoryProducts() throws Exception {
        // Parent category browse should return products in parent AND child categories
        mockMvc.perform(get("/api/categories/{categoryId}/products", parentCategory.getId())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2)); // activeProduct + childCategoryProduct
    }

    @Test
    void listProductsByCategory_withChildCategory_shouldReturnOnlyChildProducts() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/products", childCategory.getId())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Child Category Product"));
    }

    @Test
    void listProductsByCategory_whenCategoryNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/products", UUID.randomUUID())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProductsByCategory_withSortPriceAsc_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/products", parentCategory.getId())
                        .param("sort", "PRICE_ASC")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listProductsByCategory_withSortPriceDesc_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/products", parentCategory.getId())
                        .param("sort", "PRICE_DESC")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
