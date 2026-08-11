package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.RouteStatus;

import java.time.LocalDateTime;

public record RouteSummary(
        Long id,
        String destinationName,
        LocalDateTime createdAt,
        RouteStatus status
) {
}
