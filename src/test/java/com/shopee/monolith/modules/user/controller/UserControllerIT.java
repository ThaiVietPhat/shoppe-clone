package com.shopee.monolith.modules.user.controller;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.user.entity.Shop;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.ShopRepository;
import com.shopee.monolith.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserControllerIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @AfterEach
    void tearDown() {
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getCurrentUserWhenAuthenticatedShouldReturnProfileAndShopSummary() throws Exception {
        User user = userRepository.save(User.builder()
                .email("seller@shoppe.local")
                .normalizedEmail("seller@shoppe.local")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());
        Shop shop = shopRepository.save(Shop.builder()
                .ownerId(user.getId())
                .name("Seller Demo Shop")
                .build());
        String token = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("seller@shoppe.local"))
                .andExpect(jsonPath("$.data.role").value("SELLER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.shop.id").value(shop.getId().toString()))
                .andExpect(jsonPath("$.data.shop.name").value("Seller Demo Shop"));
    }

    @Test
    void getCurrentUserWhenAnonymousShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getHttpStatus()));
    }
}
