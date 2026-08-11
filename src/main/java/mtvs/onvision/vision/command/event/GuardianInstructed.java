package mtvs.onvision.vision.command.event;

import java.time.Instant;

public record GuardianInstructed(
        Long commandId,
        String content,
        Instant occurredAt,
        Long receiverId
) {
}
