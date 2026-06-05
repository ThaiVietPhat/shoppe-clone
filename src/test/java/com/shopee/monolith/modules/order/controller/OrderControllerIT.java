package com.shopee.monolith.modules.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.cart.dto.request.AddCartItemRequest;
import com.shopee.monolith.modules.cart.service.CartService;
import com.shopee.monolith.modules.inventory.service.InventoryService;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.product.entity.Category;
import com.shopee.monolith.modules.product.entity.Product;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderControllerIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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

    @Autowired
    private com.shopee.monolith.modules.inventory.repository.InventoryRepository inventoryRepository;

    @Autowired
    private com.shopee.monolith.modules.order.repository.CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private com.shopee.monolith.modules.order.repository.OrderRepository orderRepository;

    @Autowired
    private com.shopee.monolith.modules.order.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.shopee.monolith.modules.order.repository.InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private com.shopee.monolith.modules.order.repository.IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private User buyer;
    private String buyerToken;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        tearDown();

        buyer = User.builder()
                .email("buyer.order.ctrl@shoppe.local")
                .normalizedEmail("buyer.order.ctrl@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        buyer = userRepository.save(buyer);
        buyerToken = jwtTokenProvider.generateAccessToken(buyer.getId(), buyer.getRole());

        User seller = User.builder()
                .email("seller.order.ctrl@shoppe.local")
                .normalizedEmail("seller.order.ctrl@shoppe.local")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build();
        seller = userRepository.save(seller);

        Shop shop = Shop.builder()
                .ownerId(seller.getId())
                .name("Order Controller Test Shop")
                .build();
        shop = shopRepository.save(shop);

        Category category = Category.builder()
                .name("Order Ctrl Category")
                .build();
        category = categoryRepository.save(category);

        Product product = Product.builder()
                .shopId(shop.getId())
                .categoryId(category.getId())
                .name("Order Ctrl Product")
                .description("Description")
                .build();
        product = productRepository.save(product);

        variant = ProductVariant.builder()
                .productId(product.getId())
                .sku("ORD-CTRL-SKU")
                .name("Order Ctrl Variant")
                .price(BigDecimal.valueOf(10.00))
                .build();
        variant = productVariantRepository.save(variant);

        inventoryService.createInventory(variant.getId(), 50, seller.getId(), seller.getRole());
    }

    @AfterEach
    void tearDown() {
        if (buyer != null) {
            cartService.clearCart(buyer.getId());
        }
        idempotencyKeyRepository.deleteAll();
        inventoryReservationRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        checkoutSessionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void checkoutWhenUnauthenticatedShouldReturn401() throws Exception {
        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutWhenMissingIdempotencyKeyHeaderShouldReturn400() throws Exception {
        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(ErrorCode.IDEMPOTENCY_KEY_MISSING.getMessage()));
    }

    @Test
    void checkoutWhenCartIsEmptyShouldReturn400() throws Exception {
        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(ErrorCode.CART_EMPTY.getMessage()));
    }

    @Test
    void checkoutWhenValidShouldSucceed() throws Exception {
        // Prepare cart
        cartService.addItem(buyer.getId(), new AddCartItemRequest(variant.getId(), 2));

        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.checkoutSessionId").exists())
                .andExpect(jsonPath("$.data.orderIds").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));

        // Duplicate call with same key and request should return cached response
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Duplicate call with different request should return conflict
        CheckoutRequest request2 = new CheckoutRequest("456 Different Street", "Hanoi");
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.getHttpStatus()));
    }
}
