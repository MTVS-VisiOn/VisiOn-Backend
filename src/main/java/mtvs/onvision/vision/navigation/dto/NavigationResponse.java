package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.Route;

public record NavigationResponse(
        Long id,
        LocationInfo start,
        LocationInfo end,
        String report

) {
    public static NavigationResponse from(Route route) {
        LocationInfo start = new LocationInfo(route.getStartingName(), route.getStartingName(), route.getStartingLat(), route.getStartingLon(), route.getStartingAddress(), null);
        LocationInfo end = new LocationInfo(route.getDestinationName(), route.getDestinationName(), route.getDestinationLat(), route.getDestinationLon(), route.getDestinationAddress(), null);
        return new NavigationResponse(route.getId(), start, end, route.getReport());
    }
}
