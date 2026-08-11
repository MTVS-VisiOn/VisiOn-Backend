package mtvs.onvision.vision.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RegisterCodeRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.register}")
    Long expiredTime;

    private final String KEY_PREFIX = "registerCode:";

    public boolean saveIfAbsent(String code, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + code, userId.toString(), Duration.ofMillis(expiredTime)));
    }
    public Optional<Long> getToken(String code) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + code)).map(Long::valueOf);
    }

    public void delete(String code) {
        redisTemplate.delete(KEY_PREFIX+code);
    }
}
