package mtvs.onvision.vision.location.dto;

import java.time.Instant;

public record LastLocationResponse(
        Double latitude,
        Double longitude,
        String address,
        String status,
        Instant recordedAt
) {
}
