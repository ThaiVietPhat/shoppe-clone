package com.shopee.monolith.modules.review.mapper;

import com.shopee.monolith.modules.review.dto.response.ReviewResponse;
import com.shopee.monolith.modules.review.entity.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewResponse toResponse(Review review);
}
