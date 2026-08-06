package mtvs.onvision.vision.alert.repository;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.alert.domain.AlertType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class AlertNotificationRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.alert-cooldown}")
    private Long cooldownExpiredTime;

    @Value("${redis.expired.alert-notified}")
    private Long notifiedExpiredTime;

    private final String COOLDOWN_PREFIX  = "alert:cooldown:";
    private final String NOTIFIED_PREFIX  = "alert:notified:";

//    * 쿨다운을 새로 시작했으면 true, 이미 쿨다운 중이면 false
    public boolean tryStartCooldown(Long wardId, AlertType type) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + wardId + ":" + type, "1", Duration.ofSeconds(cooldownExpiredTime)));
    }

    public void clearCooldown(Long wardId, AlertType type) {
        redisTemplate.delete(COOLDOWN_PREFIX + wardId + ":" + type);
    }

    /**
     * 이 알림의 발송권을 선점했으면 true, 이미 선점됐으면 false.
     * 전송 전에 선점하므로 전송이 실패해도 키가 남는다. 재시도 스케줄러를 도입하면
     * 실패 시 키를 지우거나 선점 시점을 전송 성공 후로 옮겨야 한다.
     */
    public boolean markNotified(Long alertId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(NOTIFIED_PREFIX + alertId, "1", Duration.ofSeconds(notifiedExpiredTime)));
    }
}
