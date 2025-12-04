package LDS.Person.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import LDS.Person.websocket.OneBotWebSocketHandler;

/**
 * WebSocket 配置类 - 用于接收 OneBot 客户端连接
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private OneBotWebSocketHandler oneBotWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 OneBot WebSocket 端点
        // 客户端应连接到: ws://localhost:7090/onebot
        registry.addHandler(oneBotWebSocketHandler, "/onebot")
                .setAllowedOrigins("*");  // 允许所有来源，生产环境应限制
    }
}
