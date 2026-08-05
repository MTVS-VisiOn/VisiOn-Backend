package mtvs.onvision.vision.alert.dto;

import java.time.Instant;

public record ObstacleRequest(
        Instant occurredAt,
        Double latitude,
        Double longitude,
        String message,
        String action
) {
}
