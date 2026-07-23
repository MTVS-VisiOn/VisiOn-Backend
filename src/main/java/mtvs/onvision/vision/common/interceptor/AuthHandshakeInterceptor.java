package mtvs.onvision.vision.common.interceptor;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.TokenBody;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenProvider tokenProvider;
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String token = resolveToken(request);
        if (token != null && tokenProvider.validate(token)) {
            TokenBody tokenBody = tokenProvider.parseJwt(token);
            attributes.put("userId", tokenBody.userId());
            attributes.put("role", tokenBody.role());
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {
    }

    //토큰 헤더에서 가져오기
    private static String resolveToken(ServerHttpRequest request) {
        List<String> auth = request.getHeaders().get("Authorization");
        if (auth != null && !auth.isEmpty()) {
            return auth.get(0).replace("Bearer ", "");
        }
        return null;
    }
}
