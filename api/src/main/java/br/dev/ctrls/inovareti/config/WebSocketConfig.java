package br.dev.ctrls.inovareti.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Configuração simples de WebSocket/STOMP para eventos em tempo real.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppCorsProperties corsProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/ws/appointment-events")
            .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]));

        if (!corsProperties.getAllowedOriginPatterns().isEmpty()) {
            endpoint.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(new String[0]));
        }

        endpoint.withSockJS();
    }
}
