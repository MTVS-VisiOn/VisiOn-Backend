package mtvs.onvision.vision.command.event;

public record GuardianInstructed(
        Long commandId,
        String content,
        Long receiverId
) {
}
