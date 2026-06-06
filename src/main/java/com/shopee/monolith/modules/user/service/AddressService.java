package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.modules.user.dto.request.AddressRequest;
import com.shopee.monolith.modules.user.dto.response.AddressResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressService {

    AddressResponse createAddress(UUID userId, AddressRequest request);

    List<AddressResponse> getMyAddresses(UUID userId);

    AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request);

    void deleteAddress(UUID userId, UUID addressId);

    AddressResponse setDefaultAddress(UUID userId, UUID addressId);

    Optional<AddressResponse> findDefaultAddress(UUID userId);

    Optional<AddressResponse> findAddressByIdAndUserId(UUID addressId, UUID userId);
}
