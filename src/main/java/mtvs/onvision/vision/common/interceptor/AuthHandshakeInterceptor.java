package mtvs.onvision.vision.common.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.TokenBody;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Slf4j
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
        // 거부하면 브라우저에는 1006 만 보이고 이유가 남지 않는다.
        // "토큰을 안 실어 보냈다"와 "토큰이 무효다"를 여기서 갈라준다. 토큰 값 자체는 찍지 않는다.
        log.warn(">>> [ws] 핸드셰이크 거부 — 토큰 {}", token == null ? "없음" : "무효");
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
        // 브라우저 WebSocket 은 헤더를 못 붙이므로 쿼리 파라미터도 허용
        return UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
    }
}
