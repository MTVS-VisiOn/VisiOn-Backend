package mtvs.onvision.vision.navigation.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NavigationRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.navigation}")
    Long expiredTime;
    private final String KEY_PREFIX = "navigation:";
    private final String ROUTE_PREFIX = "route:";

    public void saveRoute(Long userId, String prefix, String json) {
        //네비이게이션 경로 갱신
        redisTemplate.opsForValue().set(KEY_PREFIX+ ROUTE_PREFIX +prefix+userId, json, Duration.ofMinutes(expiredTime));
    }

    public Optional<String> getRoute(Long userId, String prefix) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX+ ROUTE_PREFIX+prefix +userId));
    }

    public void delete(Long userId, String prefix) {
        redisTemplate.delete(KEY_PREFIX+ROUTE_PREFIX+prefix+userId);
    }
}
