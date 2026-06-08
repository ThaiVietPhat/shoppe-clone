package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.request.AddressRequest;
import com.shopee.monolith.modules.user.dto.response.AddressResponse;
import com.shopee.monolith.modules.user.entity.Address;
import com.shopee.monolith.modules.user.mapper.AddressMapper;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.AddressRepository;
import com.shopee.monolith.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest request) {
        lockActiveUser(userId);

        try {
            List<Address> existing = addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId);
            boolean shouldBeDefault = existing.isEmpty() || request.isDefault();

            if (shouldBeDefault) {
                addressRepository.resetDefaultAddresses(userId);
            }

            Address address = Address.builder()
                    .userId(userId)
                    .recipientName(request.recipientName())
                    .phone(request.phone())
                    .addressLine(request.addressLine())
                    .wardCode(request.wardCode())
                    .wardName(request.wardName())
                    .districtCode(request.districtCode())
                    .districtName(request.districtName())
                    .provinceCode(request.provinceCode())
                    .provinceName(request.provinceName())
                    .isDefault(shouldBeDefault)
                    .build();

            Address saved = addressRepository.saveAndFlush(address);
            return addressMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CONFLICT);
        }
    }

    @Override
    public List<AddressResponse> getMyAddresses(UUID userId) {
        verifyUserActive(userId);
        List<Address> addresses = addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId);
        return addressMapper.toResponseList(addresses);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        lockActiveUser(userId);

        try {
            Address address = addressRepository.findByIdAndUserId(addressId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

            List<Address> existing = addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId);
            // Invariant: at least one address must always be default.
            // If the address being updated is currently default, ignore isDefault=false from request.
            boolean keepDefault = address.isDefault() && !request.isDefault();
            boolean shouldBeDefault = keepDefault || (existing.size() == 1) || request.isDefault();

            if (shouldBeDefault && !address.isDefault()) {
                addressRepository.resetDefaultAddresses(userId);
            }

            address.update(
                    request.recipientName(),
                    request.phone(),
                    request.addressLine(),
                    request.wardCode(),
                    request.wardName(),
                    request.districtCode(),
                    request.districtName(),
                    request.provinceCode(),
                    request.provinceName(),
                    shouldBeDefault
            );

            Address updated = addressRepository.saveAndFlush(address);
            return addressMapper.toResponse(updated);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CONFLICT);
        }
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        verifyUserActive(userId);

        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            List<Address> remaining = addressRepository.findAllByUserIdOrderByIsDefaultDesc(userId);
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        lockActiveUser(userId);

        try {
            Address address = addressRepository.findByIdAndUserId(addressId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

            if (!address.isDefault()) {
                addressRepository.resetDefaultAddresses(userId);
                address.setDefault(true);
                address = addressRepository.saveAndFlush(address);
            }

            return addressMapper.toResponse(address);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CONFLICT);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse resolveCheckoutAddress(UUID userId, UUID addressId) {
        verifyUserActive(userId);
        if (addressId != null) {
            return addressRepository.findByIdAndUserId(addressId, userId)
                    .map(addressMapper::toResponse)
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        }
        return addressRepository.findDefaultByUserId(userId)
                .map(addressMapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    @Override
    public Optional<AddressResponse> findDefaultAddress(UUID userId) {
        return addressRepository.findDefaultByUserId(userId)
                .map(addressMapper::toResponse);
    }

    @Override
    public Optional<AddressResponse> findAddressByIdAndUserId(UUID addressId, UUID userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .map(addressMapper::toResponse);
    }

    private void verifyUserActive(UUID userId) {
        UserAuthenticationData userAuth = userService.findAuthenticationDataById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (userAuth.status() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    private void lockActiveUser(UUID userId) {
        var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }
}
