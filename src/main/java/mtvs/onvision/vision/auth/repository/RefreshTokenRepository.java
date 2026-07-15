package mtvs.onvision.vision.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.validations.refresh}")
    Long expiredTime;
    private static final String KEY_PREFIX = "refreshToken:";

    public void save(Long userId, String token) {
        redisTemplate.opsForValue().set(KEY_PREFIX+userId, token, Duration.ofMillis(expiredTime));
    }
    public Optional<String> getToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX+userId));
    }

    public void delete(String userId) {
        redisTemplate.delete(KEY_PREFIX+userId);
    }
}
