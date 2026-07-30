package mtvs.onvision.vision.navigation.dto;

import jakarta.validation.constraints.NotNull;
import mtvs.onvision.vision.navigation.domain.TransportMode;

public record RouteRequest(
        @NotNull
        TransportMode mode,
        Integer index
) {
}
