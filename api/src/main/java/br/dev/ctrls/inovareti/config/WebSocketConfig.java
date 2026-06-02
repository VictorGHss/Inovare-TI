package br.dev.ctrls.inovareti.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;

/**
 * Configuração simples de WebSocket/STOMP para eventos em tempo real.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppCorsProperties corsProperties;
    private final TokenPort tokenService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/ws/appointment-events")
            .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));

        if (!corsProperties.getAllowedOriginPatterns().isEmpty()) {
            endpoint.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
        }

        endpoint.withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Acesso negado: Token JWT ausente ou inválido.");
                    }
                    String token = authHeader.substring(7);
                    String email = tokenService.validateToken(token);
                    if (email == null || email.isBlank()) {
                        throw new IllegalArgumentException("Acesso negado: Token JWT inválido ou expirado.");
                    }
                }
                return message;
            }
        });
    }
}
