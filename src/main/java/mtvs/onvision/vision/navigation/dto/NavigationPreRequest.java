package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

public record NavigationPreRequest(
    TransportMode mode,
    LocationInfo start,
    LocationInfo end
) {

}
