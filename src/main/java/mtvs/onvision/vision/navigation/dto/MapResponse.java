package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record MapResponse(
        String name,
        String address,
        Double latitude,
        Double longitude,
        Integer distanceM,
        Integer etaMin,
        Instant departureTime,
        TransportMode mode,
        List<Map<String, Double>> path
) {
    public static MapResponse from(NavigationRouteReport report, TransportMode mode,  LocalDateTime departureTime) {
        NavigationSummary summary = report.summary();
        List<Map<String, Double>> path = report.report().stream()
                .flatMap(r -> r.pathToNext().stream())
                .map(p -> Map.of("latitude", p.get(0), "longitude", p.get(1)))
                .toList();
        return new MapResponse(summary.destinationName(), summary.destinationRoadAddress(), summary.destinationCoordinate().get(0),summary.destinationCoordinate().get(1),
                summary.totalDistance(), summary.totalTime()/60, departureTime.atZone(ZoneId.systemDefault()).toInstant(), mode, path);
    }

    public static MapResponse from (TransitRoute report, LocalDateTime departureTime) {
        NavigationSummary summary = report.summary();
        List<Map<String, Double>> path = report.legs().stream()
                .flatMap(leg -> leg.path().isEmpty()
                        ? leg.steps().stream().flatMap(s -> s.path().stream())   // 도보 leg
                        : leg.path().stream())                                    // 대중교통 leg
                .map(p -> Map.of("latitude", p.get(0), "longitude", p.get(1)))
                .toList();
        return new MapResponse(summary.destinationName(), summary.destinationRoadAddress(), summary.destinationCoordinate().get(0),summary.destinationCoordinate().get(1),
                summary.totalDistance(), summary.totalTime()/60, departureTime.atZone(ZoneId.systemDefault()).toInstant(), TransportMode.TRANSIT, path);
    }
}
