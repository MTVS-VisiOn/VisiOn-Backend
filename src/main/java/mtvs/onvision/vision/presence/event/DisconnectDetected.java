package mtvs.onvision.vision.presence.event;

import java.time.Instant;

public record DisconnectDetected(
        Long wardId,
        Instant occurredAt
) {
}
