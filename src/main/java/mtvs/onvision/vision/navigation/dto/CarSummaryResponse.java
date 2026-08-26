package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.util.List;

public record CarSummaryResponse(
        Integer index,
        TransportMode mode,
        Integer totalDistance,
        Integer totalTime,           //초
        Integer totalFare,           //통행료(원)
        Integer taxiFare,            //택시 예상요금(원)
        String startingName,
        String startingAddress,
        List<Double> startingCoordinate,
        String destinationName,
        String destinationAddress,
        List<Double> destinationCoordinate
) implements NavigationSummary {
}
