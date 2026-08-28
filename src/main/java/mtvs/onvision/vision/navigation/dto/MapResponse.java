package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;

/**
 * 지도 표시용 경로 1건.
 *
 * <p>{@code name}·{@code address}·{@code latitude}·{@code longitude}는 <b>목적지</b>다.
 * 이 API가 `GET /api/destination`(목적지 하나만 담던 계약)을 대체하면서 접두사 없이 굳었다.
 * 출발지는 {@code departure*}로 따로 나간다 — 이미 있던 {@code departureTime}과 같은 접두사다.
 *
 * <p><b>출발 좌표는 {@code path}의 첫 점이다.</b> 요약에 담긴 출발 좌표를 쓰면 안 된다 —
 * 그것은 클라이언트가 요청에 실어 보낸 값이고, 서버는 그 좌표를 그대로 쓰지 않는다.
 * {@code resolveStart}가 요청 좌표를 못 믿으면 저장된 최신 위치로 폴백하므로
 * 경로가 실제로 시작한 지점과 다를 수 있다(2026-08-27 실측 58m). 마커가 경로선에서
 * 떨어져 보이는 것도 같은 이유다.
 *
 * <p>반면 {@code departureName}·{@code departureAddress}는 요약에서 온다. 사용자가 고른
 * 출발지의 이름이고, 스냅된 좌표에는 주소가 없다(역지오코딩은 이 API에서 부르지 않는다).
 *
 * <p>목적지 좌표는 요약 그대로다. 티맵에 보낸 값(보행자 입구점일 수 있다)과 다를 수 있으나
 * 기존 계약이라 건드리지 않았다.
 */
public record MapResponse(
        String name,
        String address,
        Double latitude,
        Double longitude,
        String departureName,
        String departureAddress,
        Double departureLatitude,
        Double departureLongitude,
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
        List<List<Double>> points = report.routePath();
        List<Double> departure = departureOf(points, summary);
        List<Map<String, Double>> path = points.stream()
                .map(p -> Map.of("latitude", p.getFirst(), "longitude", p.getLast()))
                .toList();
        return new MapResponse(summary.destinationName(), summary.destinationAddress(), summary.destinationCoordinate().get(0),summary.destinationCoordinate().get(1),
                summary.startingName(), summary.startingAddress(),
                departure.get(0), departure.get(1),
                summary.totalDistance(), remainingDistanceM, summary.totalTime()/60, departureTime.atZone(SEOUL).toInstant(), mode, path);
    }

    public static MapResponse from (TransitRoute report, LocalDateTime departureTime, Integer remainingDistanceM) {
        NavigationSummary summary = report.summary();
        List<List<Double>> points = report.legs().stream()
                .flatMap(leg -> leg.path().isEmpty()
                        ? leg.steps().stream().flatMap(s -> s.path().stream())   // 도보 leg
                        : leg.path().stream())                                    // 대중교통 leg
                .toList();
        List<Double> departure = departureOf(points, summary);
        List<Map<String, Double>> path = points.stream()
                .map(p -> Map.of("latitude", p.get(0), "longitude", p.get(1)))
                .toList();
        return new MapResponse(summary.destinationName(), summary.destinationAddress(), summary.destinationCoordinate().get(0),summary.destinationCoordinate().get(1),
                summary.startingName(), summary.startingAddress(),
                departure.get(0), departure.get(1),
                summary.totalDistance(), remainingDistanceM, summary.totalTime()/60, departureTime.atZone(SEOUL).toInstant(), TransportMode.TRANSIT, path);
    }

    /**
     * 지도에 찍을 출발 좌표. 경로선의 첫 점이고, 경로선이 비었을 때만 요약 값으로 물러선다.
     * <p>
     * 요약 값은 클라이언트가 요청에 실어 보낸 좌표라 경로가 실제로 시작한 지점이 아닐 수 있다.
     * 그래도 폴백으로 두는 이유는, 좌표가 아예 없으면 화면에 출발 마커를 못 그리기 때문이다.
     */
    private static List<Double> departureOf(List<List<Double>> points, NavigationSummary summary) {
        return points.isEmpty() ? summary.startingCoordinate() : points.getFirst();
    }
}
