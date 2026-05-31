package com.shopee.monolith.modules.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
