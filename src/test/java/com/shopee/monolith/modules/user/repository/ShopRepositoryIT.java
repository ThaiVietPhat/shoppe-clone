package com.shopee.monolith.modules.user.repository;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.user.entity.Shop;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopRepositoryIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = User.builder()
                .email("user1@shoppe.local")
                .normalizedEmail("user1@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .email("user2@shoppe.local")
                .normalizedEmail("user2@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        user2 = userRepository.save(user2);
    }

    @AfterEach
    void tearDown() {
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveShopWhenNewShopShouldPopulateAuditFieldsAndSucceed() {
        Shop shop = Shop.builder()
                .ownerId(user1.getId())
                .name("User 1 Shop")
                .description("Desc")
                .build();

        Shop saved = shopRepository.save(shop);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(shopRepository.existsByOwnerId(user1.getId()));
    }

    @Test
    void saveShopWhenOwnerAlreadyHasShopShouldThrowDataIntegrityViolation() {
        Shop shop1 = Shop.builder()
                .ownerId(user1.getId())
                .name("User 1 Shop 1")
                .build();
        shopRepository.save(shop1);

        Shop shop2 = Shop.builder()
                .ownerId(user1.getId())
                .name("User 1 Shop 2")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> shopRepository.saveAndFlush(shop2));
    }
}
