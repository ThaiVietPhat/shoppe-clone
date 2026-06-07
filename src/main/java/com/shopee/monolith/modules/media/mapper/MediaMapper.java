package com.shopee.monolith.modules.media.mapper;

import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.entity.MediaAsset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MediaMapper {

    @Mapping(target = "publicUrl", source = "publicUrl")
    @Mapping(target = "id", source = "asset.id")
    @Mapping(target = "ownerId", source = "asset.ownerId")
    @Mapping(target = "ownerType", source = "asset.ownerType")
    @Mapping(target = "purpose", source = "asset.purpose")
    @Mapping(target = "objectKey", source = "asset.objectKey")
    @Mapping(target = "contentType", source = "asset.contentType")
    @Mapping(target = "sizeBytes", source = "asset.sizeBytes")
    @Mapping(target = "width", source = "asset.width")
    @Mapping(target = "height", source = "asset.height")
    @Mapping(target = "status", source = "asset.status")
    @Mapping(target = "createdAt", source = "asset.createdAt")
    MediaAssetResponse toResponse(MediaAsset asset, String publicUrl);
}
