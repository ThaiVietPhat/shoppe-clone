package com.shopee.monolith.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.user.dto.request.AddressRequest;
import com.shopee.monolith.modules.user.entity.Address;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.AddressRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@AutoConfigureMockMvc
class AddressControllerIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User buyer;
    private User otherBuyer;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        buyer = User.builder()
                .email("buyer.addr.it@shoppe.local")
                .normalizedEmail("buyer.addr.it@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        buyer = userRepository.save(buyer);
        token = jwtTokenProvider.generateAccessToken(buyer.getId(), buyer.getRole());

        otherBuyer = User.builder()
                .email("other.buyer.addr.it@shoppe.local")
                .normalizedEmail("other.buyer.addr.it@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        otherBuyer = userRepository.save(otherBuyer);
        otherToken = jwtTokenProvider.generateAccessToken(otherBuyer.getId(), otherBuyer.getRole());
    }

    @AfterEach
    void tearDown() {
        addressRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createAddressWhenValidShouldSucceed() throws Exception {
        AddressRequest request = AddressRequest.builder()
                .recipientName("John Doe")
                .phone("0987654321")
                .addressLine("123 Main St")
                .wardCode("1A")
                .wardName("Liễu Giai")
                .districtCode("2B")
                .districtName("Ba Đình")
                .provinceCode("3C")
                .provinceName("Hà Nội")
                .isDefault(true)
                .build();

        mockMvc.perform(post("/api/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recipientName").value("John Doe"))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void createAddressWhenAnonymousShouldReturn401() throws Exception {
        AddressRequest request = AddressRequest.builder()
                .recipientName("John Doe")
                .phone("0987654321")
                .addressLine("123 Main St")
                .wardCode("1A")
                .wardName("Liễu Giai")
                .districtCode("2B")
                .districtName("Ba Đình")
                .provinceCode("3C")
                .provinceName("Hà Nội")
                .isDefault(true)
                .build();

        mockMvc.perform(post("/api/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getHttpStatus()));
    }

    @Test
    void getMyAddressesShouldReturnUserAddresses() throws Exception {
        Address addr = Address.builder()
                .userId(buyer.getId())
                .recipientName("Home Address")
                .phone("0987654321")
                .addressLine("123 Line")
                .wardCode("1")
                .wardName("W")
                .districtCode("2")
                .districtName("D")
                .provinceCode("3")
                .provinceName("P")
                .isDefault(true)
                .build();
        addressRepository.save(addr);

        mockMvc.perform(get("/api/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].recipientName").value("Home Address"));
    }

    @Test
    void updateAddressWhenNotOwnerShouldReturn404() throws Exception {
        Address addr = Address.builder()
                .userId(buyer.getId())
                .recipientName("Home Address")
                .phone("0987654321")
                .addressLine("123 Line")
                .wardCode("1")
                .wardName("W")
                .districtCode("2")
                .districtName("D")
                .provinceCode("3")
                .provinceName("P")
                .isDefault(true)
                .build();
        addr = addressRepository.save(addr);

        AddressRequest request = AddressRequest.builder()
                .recipientName("Hack Name")
                .phone("0987654321")
                .addressLine("Hack Line")
                .wardCode("1")
                .wardName("W")
                .districtCode("2")
                .districtName("D")
                .provinceCode("3")
                .provinceName("P")
                .isDefault(true)
                .build();

        mockMvc.perform(put("/api/addresses/" + addr.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADDRESS_NOT_FOUND.getHttpStatus()));
    }

    @Test
    void setDefaultAddressShouldResetOtherAddresses() throws Exception {
        Address addr1 = Address.builder()
                .userId(buyer.getId())
                .recipientName("Addr1")
                .phone("0987654321")
                .addressLine("Line 1")
                .wardCode("1")
                .wardName("W")
                .districtCode("2")
                .districtName("D")
                .provinceCode("3")
                .provinceName("P")
                .isDefault(true)
                .build();
        addr1 = addressRepository.save(addr1);

        Address addr2 = Address.builder()
                .userId(buyer.getId())
                .recipientName("Addr2")
                .phone("0987654321")
                .addressLine("Line 2")
                .wardCode("1")
                .wardName("W")
                .districtCode("2")
                .districtName("D")
                .provinceCode("3")
                .provinceName("P")
                .isDefault(false)
                .build();
        addr2 = addressRepository.save(addr2);

        mockMvc.perform(patch("/api/addresses/" + addr2.getId() + "/default")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        Address updatedAddr1 = addressRepository.findById(addr1.getId()).orElseThrow();
        Address updatedAddr2 = addressRepository.findById(addr2.getId()).orElseThrow();

        assertTrue(updatedAddr2.isDefault());
        assertFalse(updatedAddr1.isDefault());
    }
}
