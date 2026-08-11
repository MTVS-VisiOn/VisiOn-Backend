package mtvs.onvision.vision.location.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
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
    private final String PROCESSING_SUFFIX = "processing";

    public void saveLocation(Long userId, String json) {
        //lastest location 갱신
        redisTemplate.opsForValue().set(KEY_PREFIX+LASTEST_PREFIX+userId, json, Duration.ofMinutes(expiredTime));
        //버퍼 push(전역 큐, FIFO 위해 rightPush)
        redisTemplate.opsForList().rightPush(KEY_PREFIX+BUFFER_SUFFIX, json);
    }

    public Optional<String> getLastLocation(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX+LASTEST_PREFIX+userId));
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX+userId);
    }

    /**
     * 버퍼에서 처리 중 큐로 원자적으로 옮기고, 처리 중 큐 전체를 돌려준다.
     *
     * `LPOP`이 아니라 `LMOVE`인 이유는 꺼낸 뒤 INSERT가 실패하면 되돌릴 수단이 없기 때문이다.
     * 옮겨두면 앱이 죽어도 한쪽에는 반드시 남는다.
     *
     * **처리 중 큐에 이미 남아 있는 건 직전 주기가 커밋 후 삭제 전에 죽은 것이다.**
     * 지우지 않고 이번 배치에 같이 태운다 — 중복은 `ON CONFLICT`가 흡수한다.
     */
    public List<String> moveToProcessing(int batchSize) {
        String buffer = KEY_PREFIX + BUFFER_SUFFIX;
        String processing = KEY_PREFIX + PROCESSING_SUFFIX;

        long pending = Optional.ofNullable(redisTemplate.opsForList().size(processing)).orElse(0L);
        for (long i = pending; i < batchSize; i++) {
            String moved = redisTemplate.opsForList().move(buffer, Direction.LEFT, processing, Direction.RIGHT);
            if (moved == null) break;   // 버퍼가 비었다
        }
        return Optional.ofNullable(redisTemplate.opsForList().range(processing, 0, -1))
                .orElse(List.of());
    }

    /** INSERT가 예외 없이 끝난 뒤에만 부른다. 먼저 지우면 실패 시 그대로 유실이다 */
    public void clearProcessing() {
        redisTemplate.delete(KEY_PREFIX + PROCESSING_SUFFIX);
    }
}
