package mtvs.onvision.vision.presence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PresenceRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.presence}")
    Long expiredTime;
    private final String KEY_PREFIX = "presence:";

    public void saveHeartbeat(Long userId, String json) {
        redisTemplate.opsForValue().set(KEY_PREFIX+userId, json, Duration.ofSeconds(expiredTime));
    }
    public Optional<String> getHeartbeat(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX+userId));
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX+userId);
    }
}
