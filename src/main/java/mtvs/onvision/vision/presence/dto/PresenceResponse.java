package mtvs.onvision.vision.presence.dto;

public record PresenceResponse(
        Integer battery,
        Boolean deviceConnected,
        String status
) {
}
