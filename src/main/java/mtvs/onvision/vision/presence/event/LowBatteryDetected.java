package mtvs.onvision.vision.presence.event;

import java.time.Instant;

public record LowBatteryDetected(
        Long wardId,
        Integer battery,
        Instant occurredAt
) {
}
