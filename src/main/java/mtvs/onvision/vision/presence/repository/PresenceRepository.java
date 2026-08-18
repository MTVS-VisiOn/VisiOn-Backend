package mtvs.onvision.vision.presence.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PresenceRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.presence}")
    Long expiredTime;
    private final String KEY_PREFIX = "presence:";
    private static final String WATCH_KEY = "presence:watch";

    public void saveHeartbeat(Long userId, String json) {
        redisTemplate.opsForValue().set(KEY_PREFIX+userId, json, Duration.ofSeconds(expiredTime));
    }
    public Optional<String> getLastHeartbeat(Long userId) {
        String key = KEY_PREFIX + userId;
        Optional<String> json = Optional.ofNullable(redisTemplate.opsForValue().get(key));
        log.debug("Presence lookup: key={} hit={}", key, json.isPresent());
        return json;
    }

    /** 정상 연결 상태를 갱신한다. 이미 있으면 score만 바뀐다 */
    public void markConnected(Long userId, Instant lastSync) {
        redisTemplate.opsForZSet().add(WATCH_KEY, userId.toString(), lastSync.toEpochMilli());
    }

    /** 임계값을 넘긴 감시 대상. wardId → 마지막 정상 연결 시각 */
    public Map<Long, Instant> findDisconnected(Instant threshold) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeByScoreWithScores(WATCH_KEY, Double.NEGATIVE_INFINITY, threshold.toEpochMilli());
        if (tuples == null) return Map.of();
        return tuples.stream().collect(Collectors.toMap(
                t -> Long.valueOf(t.getValue()),
                t -> Instant.ofEpochMilli(t.getScore().longValue())));
    }

    public void unwatch(Long userId) {
        redisTemplate.opsForZSet().remove(WATCH_KEY, userId.toString());
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX+userId);
    }
}
