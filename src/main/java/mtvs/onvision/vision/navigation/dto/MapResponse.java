package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;

public record MapResponse(
        String name,
        String address,
        Double latitude,
        Double longitude,
        Integer distanceM,
        Integer remainingDistanceM,
        Integer etaMin,
        Instant departureTime,
        TransportMode mode,
        List<Map<String, Double>> path
) {
    public static MapResponse from(NavigationRouteReport report, TransportMode mode, LocalDateTime departureTime,
                                   Integer remainingDistanceM) {
        NavigationSummary summary = report.summary();
        // 폴리라인 조립은 NavigationRouteReport.routePath 한 곳에서만 한다
        List<Map<String, Double>> path = report.routePath().stream()
                .map(p -> Map.of("latitude", p.getFirst(), "longitude", p.getLast()))
                .toList();
        return new MapResponse(summary.destinationName(), summary.destinationAddress(), summary.destinationCoordinate().get(0),summary.destinationCoordinate().get(1),
                summary.totalDistance(), remainingDistanceM, summary.totalTime()/60, departureTime.atZone(SEOUL).toInstant(), mode, path);
    }

    public static MapResponse from (TransitRoute report, LocalDateTime departureTime, Integer remainingDistanceM) {
        NavigationSummary summary = report.summary();
        List<Map<String, Double>> path = report.legs().stream()
                .flatMap(leg -> leg.path().isEmpty()
                        ? leg.steps().stream().flatMap(s -> s.path().stream())   // 도보 leg
                        : leg.path().stream())                                    // 대중교통 leg
                .map(p -> Map.of("latitude", p.get(0), "longitude", p.get(1)))
                .toList();
        return new MapResponse(summary.destinationName(), summary.destinationAddress(), summary.destinationCoordinate().get(0),summary.destinationCoordinate().get(1),
                summary.totalDistance(), remainingDistanceM, summary.totalTime()/60, departureTime.atZone(SEOUL).toInstant(), TransportMode.TRANSIT, path);
    }
}
