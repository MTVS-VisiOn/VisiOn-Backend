package mtvs.onvision.vision.alert.dto;

import mtvs.onvision.vision.alert.domain.AlertDelivery;
import mtvs.onvision.vision.alert.domain.AlertType;

/** 재전송에 필요한 값만 뽑아 트랜잭션 밖으로 들고 나간다 */
public record RetryTarget(
        Long deliveryId,
        Long alertId,
        AlertType type,
        String fid
) {
    public static RetryTarget from(AlertDelivery delivery) {
        return new RetryTarget(delivery.getId(), delivery.getAlert().getId(),
                delivery.getAlert().getType(), delivery.getFid());
    }
}
