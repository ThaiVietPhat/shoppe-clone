package com.shopee.monolith.modules.chat.config;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MAX_FRAME_SIZE_BYTES = 64 * 1024;
    private static final int SEND_BUFFER_SIZE_BYTES = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final AuthSecurityProperties securityProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(securityProperties.getCors().getAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(MAX_FRAME_SIZE_BYTES);
        registry.setSendBufferSizeLimit(SEND_BUFFER_SIZE_BYTES);
        registry.setSendTimeLimit(SEND_TIME_LIMIT_MS);
    }
}
