package mtvs.onvision.vision.alert.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.alert.dto.RetryTarget;
import mtvs.onvision.vision.alert.service.AlertDeliveryService;
import mtvs.onvision.vision.alert.service.FcmService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 2단 재전송. 즉시 재시도(FcmService)로도 못 보낸 건을 나중에 다시 본다.
 * <p>
 * 리스너 경로가 아니므로 {@code markNotified}를 타지 않는다.
 * 타면 멱등 키가 이미 잡혀 있어 재발송이 죽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "alert.retry.enabled", havingValue = "true", matchIfMissing = true)  //테스트에선 안돌게 막음
public class AlertRetryScheduler {

    private final AlertDeliveryService  alertDeliveryService;
    private final FcmService fcmService;

    @Scheduled(fixedDelayString = "${alert.retry.interval}")
    public void retryFailedDeliveries() {
        alertDeliveryService.expireOldDeliveries();

        List<RetryTarget> targets = alertDeliveryService.findRetryTargets();
        if (targets.isEmpty()) return;
        log.info("Found {} retry targets", targets.size());
        for (RetryTarget target : targets) {
            NotifyStatus status = fcmService.sendToDevice(
                    target.alertId(), target.type(), target.occurredAt(), target.fid());
            alertDeliveryService.applyResult(target.deliveryId(), status);
        }
    }
}
