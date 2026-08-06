package mtvs.onvision.vision.alert.dto;

import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertDelivery;
import mtvs.onvision.vision.alert.domain.AlertType;

import java.time.Instant;

/** 재전송에 필요한 값만 뽑아 트랜잭션 밖으로 들고 나간다 */
public record RetryTarget(
        Long deliveryId,
        Long alertId,
        AlertType type,
        Instant occurredAt,
        String fid
) {
    public static RetryTarget from(AlertDelivery delivery) {
        Alert alert = delivery.getAlert();
        return new RetryTarget(delivery.getId(), alert.getId(),
                alert.getType(), alert.getOccurredAt(), delivery.getFid());
    }
}
