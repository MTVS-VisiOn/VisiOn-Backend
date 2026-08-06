package mtvs.onvision.vision.alert.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.presence.event.LowBatteryDetected;
import mtvs.onvision.vision.alert.event.ObstacleDetected;
import mtvs.onvision.vision.alert.repository.AlertNotificationRepository;
import mtvs.onvision.vision.alert.service.AlertDeliveryService;
import mtvs.onvision.vision.alert.service.AlertService;
import mtvs.onvision.vision.alert.service.FcmService;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class AlertListener {
    private final FcmService fcmService;
    private final UserService userService;
    private final AlertService alertService;
    private final AlertDeliveryService  alertDeliveryService;
    private final AlertNotificationRepository  alertNotificationRepository;

    // 장애물 이벤트 처리 - 알림 보내기 -> 알림 누르면 상세 페이지로 이동
    @Async
    @TransactionalEventListener
    public void handleObstacleEvent(ObstacleDetected event) {
        log.info("Received obstacle alert event {}", event);
        if (!alertNotificationRepository.markNotified(event.alertId())) {
            log.info("Duplicate alert notification skipped: alertId={}", event.alertId());
            return;
        }
        deliver(event.alertId(), AlertType.OBSTACLE, event.occurredAt(), event.wardId());
    }

    @Async
    @EventListener
    public void handleBatteryEvent(LowBatteryDetected event) {
        log.info("Received battery alert event {}", event);
        alertService.detectBatteryLow(event.battery(), event.occurredAt(), event.wardId()).ifPresent(
                alertId -> deliver(alertId,AlertType.LOW_BATTERY, event.occurredAt(), event.wardId()));
    }

    private void deliver(Long alertId, AlertType type, Instant occurredAt, Long wardId) {
        Long guardianId = userService.getGuardianIdFromWardId(wardId);
        List<String> fids = userService.getFids(guardianId);
        alertDeliveryService.createPending(alertId, fids);
        Map<String, NotifyStatus> results = fcmService.sendNotification(alertId, type, occurredAt, fids);
        alertDeliveryService.applyResults(alertId, results);
    }
}
