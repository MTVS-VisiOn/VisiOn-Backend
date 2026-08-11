package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import mtvs.onvision.vision.navigation.domain.Route;
import mtvs.onvision.vision.navigation.domain.TransportMode;

public record NavigationResponse(
        Long id,
        TransportMode mode,
        LocationInfo start,
        LocationInfo end,
        Integer remainingDistanceM,
        @JsonRawValue
        String report

) {
    public static NavigationResponse from(Route route, Integer remainingDistanceM) {
        LocationInfo start = new LocationInfo(route.getStartingName(), route.getStartingName(), route.getStartingLat(), route.getStartingLon(), route.getStartingAddress(), null);
        LocationInfo end = new LocationInfo(route.getDestinationName(), route.getDestinationName(), route.getDestinationLat(), route.getDestinationLon(), route.getDestinationAddress(), null);
        return new NavigationResponse(route.getId(),route.getMode(), start, end, remainingDistanceM, route.getReport());
    }
}
