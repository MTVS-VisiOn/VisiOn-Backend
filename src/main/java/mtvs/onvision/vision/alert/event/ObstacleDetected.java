package mtvs.onvision.vision.alert.event;

import java.time.Instant;

/**
 * 리스너: {@code AlertListener#handleAlertEvent}
 * <p>
 * {@code occurredAt}은 푸시 문구에 넣는다. 엔티티를 싣지 않기 위해 값으로 들고 나른다.
 */
public record ObstacleDetected (
        Long alertId,
        Long wardId,
        Instant occurredAt
){
}
