package mtvs.onvision.vision.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertDelivery;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.alert.dto.RetryTarget;
import mtvs.onvision.vision.alert.repository.AlertDeliveryRepository;
import mtvs.onvision.vision.alert.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDeliveryService {

    private final AlertDeliveryRepository alertDeliveryRepository;
    private final AlertRepository alertRepository;

    @Value("${alert.retry.expire-minutes}")
    private long expireMinutes;

    private static final Set<NotifyStatus> RETRY_TARGET = EnumSet.of(NotifyStatus.PENDING, NotifyStatus.FAILED);

    // 발송 목록 만들기
    @Transactional
    public void createPending(Long alertId, List<String> fids) {
        if (fids.isEmpty()) return;
        Alert alert = alertRepository.getReferenceById(alertId);
        alertDeliveryRepository.saveAll(fids.stream().map(f -> new AlertDelivery(alert, f)).toList());
    }

    //발송 결과 반영
    @Transactional
    public void applyResults(Long alterId, Map<String, NotifyStatus> results) {
        alertDeliveryRepository.findAllByAlertId(alterId).stream()
                .filter(delivery -> results.containsKey(delivery.getFid()))
                .forEach(delivery -> delivery.markResult(results.get(delivery.getFid())));
    }

    @Transactional
    public void applyResult(Long deliveryId, NotifyStatus status) {
        alertDeliveryRepository.findById(deliveryId).ifPresent(delivery -> delivery.markResult(status));
    }

    // 임계값을 넘긴 미발송 건은 포기, 로그남기기
    @Transactional
    public void expireOldDeliveries() {
        List<AlertDelivery> targets =
                alertDeliveryRepository.findAllByStatusInAndAlertOccurredAtLessThan(RETRY_TARGET, threshold());
        targets.forEach(AlertDelivery::expire);
        if (!targets.isEmpty()) {
            log.warn("Expired {} alert deliveries", targets.size());
        }
    }

    //재전송 대상을 값으로 뽑는다. FCM 호출은 트랜잭션 밖에서 해야 커넥션을 잡아두지 않는다
    @Transactional(readOnly = true)
    public List<RetryTarget> findRetryTargets() {
        return alertDeliveryRepository
                .findAllByStatusInAndAlertOccurredAtGreaterThanEqual(RETRY_TARGET, threshold())
                .stream().map(RetryTarget::from).toList();
    }


    private Instant threshold() {
        return Instant.now().minus(Duration.ofMinutes(expireMinutes));
    }
}
