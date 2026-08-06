package mtvs.onvision.vision.presence.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.presence.event.DisconnectDetected;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 일정 시간 정상 heartbeat가 없는 피보호자를 찾아 연결 끊김 이벤트를 발행한다.
 * 발행한 대상은 감시 목록에서 지운다. 재연결하면 heartbeat가 다시 넣는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "presence.disconnect.enabled", havingValue = "true", matchIfMissing = true)
public class DisconnectMonitor {

    private final PresenceRepository presenceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${presence.disconnect.threshold-seconds}")
    private long thresholdSeconds;

    /** 기동 시각. 재시작 직후 폭주를 막는 데 쓴다 */
    private final Instant startedAt = Instant.now();

    @Scheduled(fixedDelayString = "${presence.disconnect.interval}")
    public void detectDisconnected() {
        // 살아 있는 기기가 heartbeat로 score를 갱신할 시간을 준다.
        // 없으면 배포 때마다 전원에게 '연결 끊김'이 나간다
        if (Instant.now().isBefore(startedAt.plusSeconds(thresholdSeconds))) return;

        Instant threshold = Instant.now().minusSeconds(thresholdSeconds);
        presenceRepository.findDisconnected(threshold).forEach((wardId, lastSync) -> {
            log.info("Ward disconnected: wardId={} lastSync={}", wardId, lastSync);
            eventPublisher.publishEvent(new DisconnectDetected(wardId, lastSync));
            presenceRepository.unwatch(wardId);
        });
    }
}
