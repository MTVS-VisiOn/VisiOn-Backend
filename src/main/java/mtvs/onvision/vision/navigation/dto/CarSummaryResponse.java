package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.util.List;

public record CarSummaryResponse(
        TransportMode mode,
        Integer totalDistance,
        Integer totalTime,           //초
        Integer totalFare,           //통행료(원)
        Integer taxiFare,            //택시 예상요금(원)
        String startingName,
        String startingRoadAddress,
        List<Double> startingCoordinate,
        String destinationName,
        String destinationRoadAddress,
        List<Double> destinationCoordinate
) implements NavigationSummary {
}
