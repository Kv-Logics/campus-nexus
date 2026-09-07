package com.kvlogics.campusnexus.config;

import com.kvlogics.campusnexus.relay.RelayTunnelHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RelayTunnelHandler relayTunnelHandler;

    public WebSocketConfig(RelayTunnelHandler relayTunnelHandler) {
        this.relayTunnelHandler = relayTunnelHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(relayTunnelHandler, "/ws/relay")
                .setAllowedOrigins("*");
    }
}
