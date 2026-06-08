package com.shopee.monolith.modules.user.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.response.ApiResponse;
import com.shopee.monolith.common.response.SwaggerResponses;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.media.dto.response.MediaAssetResponse;
import com.shopee.monolith.modules.media.entity.MediaPurpose;
import com.shopee.monolith.modules.media.service.MediaService;
import com.shopee.monolith.modules.user.dto.internal.ShopLookupData;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.response.CurrentUserResponse;
import com.shopee.monolith.modules.user.dto.response.CurrentUserShopResponse;
import com.shopee.monolith.modules.user.dto.response.UserSwaggerResponses;
import com.shopee.monolith.modules.user.service.ShopService;
import com.shopee.monolith.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile APIs")
public class UserController {

    private final UserService userService;
    private final ShopService shopService;
    private final MediaService mediaService;

    @Operation(
            summary = "Get current user",
            description = "Returns the authenticated user's minimum profile and owned shop summary for client app chrome.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Current user retrieved successfully.",
            content = @Content(schema = @Schema(implementation = UserSwaggerResponses.ApiResponseCurrentUserResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Authentication required.",
            content = @Content(schema = @Schema(implementation = SwaggerResponses.ApiResponseVoid.class))
    )
    @GetMapping("/api/users/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal AccessTokenClaims claims) {
        if (claims == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UserAuthenticationData user = userService.findAuthenticationDataById(claims.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        CurrentUserShopResponse shop = shopService.findShopLookupDataByOwnerId(claims.userId())
                .map(this::toShopResponse)
                .orElse(null);
        MediaAssetResponse avatar = mediaService.findLatestReadyMedia(user.id(), "USER", MediaPurpose.AVATAR)
                .orElse(null);
        return ApiResponse.success(CurrentUserResponse.builder()
                .id(user.id())
                .email(user.email())
                .role(user.role())
                .status(user.status())
                .avatar(avatar)
                .shop(shop)
                .build());
    }

    private CurrentUserShopResponse toShopResponse(ShopLookupData shop) {
        MediaAssetResponse logo = mediaService.findLatestReadyMedia(shop.id(), "SHOP", MediaPurpose.SHOP_LOGO)
                .orElse(null);
        return CurrentUserShopResponse.builder()
                .id(shop.id())
                .name(shop.name())
                .logo(logo)
                .build();
    }
}
