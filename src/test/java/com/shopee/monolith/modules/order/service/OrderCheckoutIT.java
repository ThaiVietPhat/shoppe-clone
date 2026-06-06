package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.cart.dto.request.AddCartItemRequest;
import com.shopee.monolith.modules.cart.service.CartService;
import com.shopee.monolith.modules.inventory.dto.response.InventoryResponse;
import com.shopee.monolith.modules.inventory.service.InventoryService;
import com.shopee.monolith.modules.order.dto.request.CheckoutRequest;
import com.shopee.monolith.modules.order.dto.response.CheckoutResponse;
import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.entity.InventoryReservation;
import com.shopee.monolith.modules.order.entity.Order;
import com.shopee.monolith.modules.order.entity.OrderItem;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.model.InventoryReservationStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import com.shopee.monolith.modules.order.repository.InventoryReservationRepository;
import com.shopee.monolith.modules.order.repository.OrderItemRepository;
import com.shopee.monolith.modules.order.repository.OrderRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCheckoutIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private InventoryService inventoryService;

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
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private com.shopee.monolith.modules.inventory.repository.InventoryRepository inventoryRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.shopee.monolith.modules.order.repository.IdempotencyKeyRepository idempotencyKeyRepository;

    private User buyer;
    private User seller1;
    private User seller2;
    private Shop shop1;
    private Shop shop2;
    private ProductVariant variant1;
    private ProductVariant variant2;

    @BeforeEach
    void setUp() {
        tearDown();

        buyer = User.builder()
                .email("buyer.checkout.it@shoppe.local")
                .normalizedEmail("buyer.checkout.it@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        buyer = userRepository.save(buyer);

        seller1 = User.builder()
                .email("seller1.checkout.it@shoppe.local")
                .normalizedEmail("seller1.checkout.it@shoppe.local")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build();
        seller1 = userRepository.save(seller1);

        shop1 = Shop.builder()
                .ownerId(seller1.getId())
                .name("Checkout Shop 1")
                .build();
        shop1 = shopRepository.save(shop1);

        seller2 = User.builder()
                .email("seller2.checkout.it@shoppe.local")
                .normalizedEmail("seller2.checkout.it@shoppe.local")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build();
        seller2 = userRepository.save(seller2);

        shop2 = Shop.builder()
                .ownerId(seller2.getId())
                .name("Checkout Shop 2")
                .build();
        shop2 = shopRepository.save(shop2);

        Category category = Category.builder()
                .name("Checkout Category")
                .build();
        category = categoryRepository.save(category);

        Product product1 = Product.builder()
                .shopId(shop1.getId())
                .categoryId(category.getId())
                .name("Checkout Product 1")
                .build();
        product1 = productRepository.save(product1);

        Product product2 = Product.builder()
                .shopId(shop2.getId())
                .categoryId(category.getId())
                .name("Checkout Product 2")
                .build();
        product2 = productRepository.save(product2);

        variant1 = ProductVariant.builder()
                .productId(product1.getId())
                .sku("ORD-CH-V1")
                .name("V1")
                .price(BigDecimal.valueOf(10.00))
                .build();
        variant1 = productVariantRepository.save(variant1);

        variant2 = ProductVariant.builder()
                .productId(product2.getId())
                .sku("ORD-CH-V2")
                .name("V2")
                .price(BigDecimal.valueOf(20.00))
                .build();
        variant2 = productVariantRepository.save(variant2);

        inventoryService.createInventory(variant1.getId(), 10, seller1.getId(), seller1.getRole());
        inventoryService.createInventory(variant2.getId(), 20, seller2.getId(), seller2.getRole());
    }

    @AfterEach
    void tearDown() {
        if (buyer != null) {
            cartService.clearCart(buyer.getId());
        }
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
    void checkoutShouldCreateEntitiesAndReserveStockCorrectly() {
        cartService.addItem(buyer.getId(), new AddCartItemRequest(variant1.getId(), 2));
        cartService.addItem(buyer.getId(), new AddCartItemRequest(variant2.getId(), 3));

        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        String idempotencyKey = UUID.randomUUID().toString();

        CheckoutResponse response = orderService.checkout(buyer.getId(), request, idempotencyKey);

        assertNotNull(response);
        assertNotNull(response.checkoutSessionId());
        assertEquals(2, response.orderIds().size());
        assertEquals(new BigDecimal("80.00"), response.totalAmount());
        assertEquals("PENDING_PAYMENT", response.status());

        // Verify database records
        CheckoutSession session = checkoutSessionRepository.findById(response.checkoutSessionId()).orElseThrow();
        assertEquals(CheckoutSessionStatus.PENDING_PAYMENT, session.getStatus());
        assertEquals(new BigDecimal("80.00"), session.getTotalAmount());

        List<Order> orders = orderRepository.findAll();
        assertEquals(2, orders.size());
        assertTrue(orders.stream().anyMatch(o -> o.getShopId().equals(shop1.getId()) && o.getTotalAmount().compareTo(new BigDecimal("20.00")) == 0));
        assertTrue(orders.stream().anyMatch(o -> o.getShopId().equals(shop2.getId()) && o.getTotalAmount().compareTo(new BigDecimal("60.00")) == 0));

        List<OrderItem> items = orderItemRepository.findAll();
        assertEquals(2, items.size());

        // Verify inventory reservation ledger
        List<InventoryReservation> reservations = inventoryReservationRepository.findAll();
        assertEquals(2, reservations.size());
        assertTrue(reservations.stream().anyMatch(r -> r.getVariantId().equals(variant1.getId()) && r.getQuantity() == 2 && r.getStatus() == InventoryReservationStatus.RESERVED));
        assertTrue(reservations.stream().anyMatch(r -> r.getVariantId().equals(variant2.getId()) && r.getQuantity() == 3 && r.getStatus() == InventoryReservationStatus.RESERVED));

        // Verify inventory stock levels
        InventoryResponse inv1 = inventoryService.getInventoryByVariantId(variant1.getId(), seller1.getId(), seller1.getRole());
        assertEquals(8, inv1.availableStock());
        assertEquals(2, inv1.reservedStock());

        InventoryResponse inv2 = inventoryService.getInventoryByVariantId(variant2.getId(), seller2.getId(), seller2.getRole());
        assertEquals(17, inv2.availableStock());
        assertEquals(3, inv2.reservedStock());

        // Verify cart is cleared
        assertTrue(cartService.getCart(buyer.getId()).items().isEmpty());
    }

    @Test
    void checkoutWhenCartMutatedAfterSnapshotShouldNotDeleteCartMutation() {
        // Prepare cart with variant1 (this will be part of the checkout snapshot)
        cartService.addItem(buyer.getId(), new AddCartItemRequest(variant1.getId(), 2));

        // When inventoryService.reserve is called during checkout (mid-transaction), mutate the cart
        org.mockito.Mockito.doAnswer(invocation -> {
            cartService.addItem(buyer.getId(), new AddCartItemRequest(variant2.getId(), 1));
            invocation.callRealMethod();
            return null;
        }).when(inventoryService).reserve(org.mockito.ArgumentMatchers.anyList());

        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        String idempotencyKey = UUID.randomUUID().toString();

        CheckoutResponse response = orderService.checkout(buyer.getId(), request, idempotencyKey);

        assertNotNull(response);

        // Verify that the cart was NOT cleared because of the version mismatch (mutated cart remains fully intact)
        var cart = cartService.getCart(buyer.getId());
        assertEquals(2, cart.items().size());
        assertTrue(cart.items().stream().anyMatch(i -> i.variantId().equals(variant1.getId())));
        assertTrue(cart.items().stream().anyMatch(i -> i.variantId().equals(variant2.getId())));
    }

    @Test
    void checkoutWithExpiredIdempotencyKeyShouldSucceedAndResetKey() {
        cartService.addItem(buyer.getId(), new AddCartItemRequest(variant1.getId(), 2));

        CheckoutRequest request = new CheckoutRequest("123 Street", "Hanoi");
        String idempotencyKey = UUID.randomUUID().toString();

        // 1. First checkout attempt with the key
        CheckoutResponse response1 = orderService.checkout(buyer.getId(), request, idempotencyKey);
        assertNotNull(response1);

        // 2. Fetch the created idempotency key and set its expiration in the past
        var dbKey = idempotencyKeyRepository.findByActorIdAndOperationAndIdempotencyKey(buyer.getId(), "CHECKOUT", idempotencyKey)
                .orElseThrow();

        // Manually expire the key in DB by saving it with past expiresAt
        com.shopee.monolith.modules.order.entity.IdempotencyKey expiredKey = com.shopee.monolith.modules.order.entity.IdempotencyKey.builder()
                .id(dbKey.getId())
                .actorId(dbKey.getActorId())
                .operation(dbKey.getOperation())
                .idempotencyKey(dbKey.getIdempotencyKey())
                .requestHash(dbKey.getRequestHash())
                .status(dbKey.getStatus())
                .responseBody(dbKey.getResponseBody())
                .expiresAt(java.time.Instant.now().minus(java.time.Duration.ofHours(1)))
                .createdAt(dbKey.getCreatedAt())
                .updatedAt(dbKey.getUpdatedAt())
                .build();
        idempotencyKeyRepository.save(expiredKey);

        // 3. Add items to cart again since checkout cleared it
        cartService.addItem(buyer.getId(), new AddCartItemRequest(variant1.getId(), 3));

        // 4. Run second checkout with the SAME idempotency key but a different cart (different request/data)
        CheckoutResponse response2 = orderService.checkout(buyer.getId(), request, idempotencyKey);
        assertNotNull(response2);

        // Assert it did not replay the old response, but ran a new one with correct amount
        assertEquals(new BigDecimal("30.00"), response2.totalAmount());

        // Verify key is updated in DB to the new expiration and response
        var updatedKey = idempotencyKeyRepository.findByActorIdAndOperationAndIdempotencyKey(buyer.getId(), "CHECKOUT", idempotencyKey)
                .orElseThrow();
        assertTrue(updatedKey.getExpiresAt().isAfter(java.time.Instant.now()));
        assertTrue(updatedKey.getResponseBody().contains("30.00"));
        assertEquals(dbKey.getId(), updatedKey.getId()); // verify the UUID remains identical (same row updated)
    }
}
