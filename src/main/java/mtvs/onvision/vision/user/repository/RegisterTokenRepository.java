package mtvs.onvision.vision.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RegisterTokenRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.validations.register}")
    Long expiredTime;

    private final String KEY_PREFIX = "registerToken:";

    public void save(Long userId, String token) {
        redisTemplate.opsForValue().set(KEY_PREFIX+userId, token, Duration.ofMillis(expiredTime));
    }
    public Optional<String> getToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX+userId));
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX+userId);
    }
}
