package mtvs.onvision.vision.signalling.event;

import java.time.Instant;

public record GuardianEntered(
        Long roomId,
        Long receiverId,
        Instant occurredAt
) {
}
