package mtvs.onvision.vision.location.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.location.service.LocationHistoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 버퍼를 주기적으로 비운다. 트리거만 하고 조율은 서비스가 한다.
 *
 * 스케줄러를 서비스와 분리한 이유는 테스트에서 주기를 기다리지 않고 `flush()`를 직접 부르기 위해서,
 * 그리고 `location.flush.enabled=false`로 스케줄만 끄기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "location.flush.enabled", havingValue = "true", matchIfMissing = true)
public class LocationFlushScheduler {

    private final LocationHistoryService locationHistoryService;

    @Scheduled(fixedDelayString = "${location.flush.interval}")
    public void flush() {
        try {
            locationHistoryService.flush();
        } catch (Exception e) {
            // 예외를 던지면 이후 주기가 통째로 멈춘다.
            // 데이터는 처리 중 큐에 남아 다음 주기에 재시도되므로 여기서는 삼키고 기록만 한다
            log.error("Location flush failed: {}", e.getMessage(), e);
        }
    }
}
