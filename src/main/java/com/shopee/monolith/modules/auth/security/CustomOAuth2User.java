package com.shopee.monolith.modules.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User oauth2User;
    private final UUID userId;
    private final String role;

    public CustomOAuth2User(OAuth2User oauth2User, UUID userId, String role) {
        this.oauth2User = oauth2User;
        this.userId = userId;
        this.role = role;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getName() {
        return oauth2User.getName();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }
}
