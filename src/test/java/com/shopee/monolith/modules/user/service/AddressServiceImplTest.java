package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.request.AddressRequest;
import com.shopee.monolith.modules.user.dto.response.AddressResponse;
import com.shopee.monolith.modules.user.entity.Address;
import com.shopee.monolith.modules.user.mapper.AddressMapper;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private AddressServiceImpl addressService;

    private final UUID userId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private AddressRequest request;
    private Address address;
    private AddressResponse response;
    private UserAuthenticationData activeUser;

    @BeforeEach
    void setUp() {
        request = AddressRequest.builder()
                .recipientName("John Doe")
                .phone("0987654321")
                .addressLine("123 Main Street")
                .wardCode("20314")
                .wardName("Liễu Giai")
                .districtCode("1442")
                .districtName("Ba Đình")
                .provinceCode("201")
                .provinceName("Hà Nội")
                .isDefault(false)
                .build();

        address = Address.builder()
                .id(addressId)
                .userId(userId)
                .recipientName("John Doe")
                .phone("0987654321")
                .addressLine("123 Main Street")
                .wardCode("20314")
                .wardName("Liễu Giai")
                .districtCode("1442")
                .districtName("Ba Đình")
                .provinceCode("201")
                .provinceName("Hà Nội")
                .isDefault(true)
                .build();

        response = AddressResponse.builder()
                .id(addressId)
                .userId(userId)
                .recipientName("John Doe")
                .phone("0987654321")
                .addressLine("123 Main Street")
                .wardCode("20314")
                .wardName("Liễu Giai")
                .districtCode("1442")
                .districtName("Ba Đình")
                .provinceCode("201")
                .provinceName("Hà Nội")
                .isDefault(true)
                .build();

        activeUser = UserAuthenticationData.builder()
                .id(userId)
                .email("buyer@shoppe.local")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createAddressWhenFirstAddressShouldForceDefault() {
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId)).thenReturn(Collections.emptyList());
        when(addressRepository.save(any(Address.class))).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(response);

        AddressResponse result = addressService.createAddress(userId, request);

        assertEquals(response, result);
        verify(addressRepository).resetDefaultAddresses(userId);
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void createAddressWhenNotFirstAndIsDefaultTrueShouldResetOthers() {
        AddressRequest defaultRequest = AddressRequest.builder()
                .recipientName("John Doe")
                .phone("0987654321")
                .addressLine("123 Main Street")
                .wardCode("20314")
                .wardName("Liễu Giai")
                .districtCode("1442")
                .districtName("Ba Đình")
                .provinceCode("201")
                .provinceName("Hà Nội")
                .isDefault(true)
                .build();

        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId)).thenReturn(List.of(address));
        when(addressRepository.save(any(Address.class))).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(response);

        AddressResponse result = addressService.createAddress(userId, defaultRequest);

        assertEquals(response, result);
        verify(addressRepository).resetDefaultAddresses(userId);
    }

    @Test
    void getMyAddressesShouldReturnList() {
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId)).thenReturn(List.of(address));
        when(addressMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<AddressResponse> result = addressService.getMyAddresses(userId);

        assertEquals(1, result.size());
        assertEquals(response, result.get(0));
    }

    @Test
    void updateAddressWhenNotFoundShouldThrowException() {
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> addressService.updateAddress(userId, addressId, request));
    }

    @Test
    void updateAddressShouldSucceed() {
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId)).thenReturn(List.of(address));
        when(addressRepository.save(address)).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(response);

        AddressResponse result = addressService.updateAddress(userId, addressId, request);

        assertEquals(response, result);
    }

    @Test
    void deleteAddressWhenDefaultShouldSetFirstRemainingAsDefault() {
        Address remainingAddress = Address.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .recipientName("Jane Doe")
                .phone("0987654322")
                .isDefault(false)
                .build();

        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        
        List<Address> remainingList = new ArrayList<>();
        remainingList.add(remainingAddress);
        when(addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId)).thenReturn(remainingList);

        addressService.deleteAddress(userId, addressId);

        verify(addressRepository).delete(address);
        verify(addressRepository).save(remainingAddress);
    }

    @Test
    void setDefaultAddressShouldResetOthersAndSetTrue() {
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(activeUser));
        
        Address nonDefaultAddress = Address.builder()
                .id(addressId)
                .userId(userId)
                .recipientName("John Doe")
                .phone("0987654321")
                .isDefault(false)
                .build();

        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(nonDefaultAddress));
        when(addressRepository.save(nonDefaultAddress)).thenReturn(nonDefaultAddress);
        when(addressMapper.toResponse(nonDefaultAddress)).thenReturn(response);

        AddressResponse result = addressService.setDefaultAddress(userId, addressId);

        verify(addressRepository).resetDefaultAddresses(userId);
        assertEquals(response, result);
    }
}
