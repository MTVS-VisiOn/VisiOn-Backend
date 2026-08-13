package mtvs.onvision.vision.presence.dto;

import mtvs.onvision.vision.presence.domain.GuardianStreamStatus;

public record PresenceResponse(
        Integer battery,
        Boolean deviceConnected,
        Boolean deviceNetwork,
        String status,
        GuardianStreamStatus guardianStreamStatus
) {
}
