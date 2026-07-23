package mtvs.onvision.vision.common.config;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.interceptor.AuthHandshakeInterceptor;
import mtvs.onvision.vision.signalling.SignalHandler;
import mtvs.onvision.vision.user.repository.RelationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final RelationRepository relationRepository;
    private final JwtTokenProvider tokenProvider;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingSocketHandler(relationRepository), "/signal")  //핸들러와 연결될 endpoint
                .addInterceptors(new AuthHandshakeInterceptor(tokenProvider))
                .setAllowedOriginPatterns("*")                 //CORS 설정
                .withSockJS();

        // 테스트용 raw WS (SockJS 없음)
        registry.addHandler(signalingSocketHandler(relationRepository), "/signal-raw")
                .addInterceptors(new AuthHandshakeInterceptor(tokenProvider))
                .setAllowedOriginPatterns("*");//SockJS 설정
    }



    @Bean
    public WebSocketHandler signalingSocketHandler(RelationRepository relationRepository) {
        return new SignalHandler(relationRepository);
    }
}
