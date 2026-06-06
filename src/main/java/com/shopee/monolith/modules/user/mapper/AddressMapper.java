package com.shopee.monolith.modules.user.mapper;

import com.shopee.monolith.modules.user.dto.response.AddressResponse;
import com.shopee.monolith.modules.user.entity.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    @Mapping(source = "default", target = "isDefault")
    AddressResponse toResponse(Address address);

    List<AddressResponse> toResponseList(List<Address> addresses);
}
