package mtvs.onvision.vision.location.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RealtimeLocationRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.location}")
    Long expiredTime;
    private final String KEY_PREFIX = "location:";
    private final String LASTEST_PREFIX = "latest:";
    private final String BUFFER_SUFFIX = "buffer";

    public void saveLocation(Long userId, String json) {
        //lastest location 갱신
        redisTemplate.opsForValue().set(KEY_PREFIX+LASTEST_PREFIX+userId, json, Duration.ofMinutes(expiredTime));
        //버퍼 push(전역 큐, FIFO 위해 rightPush)
        redisTemplate.opsForList().rightPush(KEY_PREFIX+BUFFER_SUFFIX, json);
    }

    public Optional<String> getLastLocation(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX+userId));
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX+userId);
    }

}
