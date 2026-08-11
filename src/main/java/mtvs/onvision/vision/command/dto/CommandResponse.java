package mtvs.onvision.vision.command.dto;

import mtvs.onvision.vision.command.domain.Command;

import java.time.Instant;

public record CommandResponse(
        Long id,
        String content,
        Instant occurredAt) {
    public static CommandResponse from(Command c) {
        return new CommandResponse(c.getId(), c.getContent(), c.getOccurredAt());
    }
}
