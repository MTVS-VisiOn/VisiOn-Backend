package mtvs.onvision.vision.navigation.service;

import mtvs.onvision.vision.navigation.dto.NavigationRouteReport;
import mtvs.onvision.vision.navigation.dto.RouteStep;
import mtvs.onvision.vision.navigation.dto.TransitRoute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 저장된 경로와 피보호자의 현재 좌표로 남은 거리를 구한다.
 * <p>
 * 서버는 진행률을 저장하지 않는다. 그래서 매 호출마다 경로 폴리라인 위에 현재 좌표를 투영해
 * 얼마나 왔는지를 되짚는다.
 * <p>
 * 꼭짓점만 비교하면 해상도가 지점 간격(도보 기준 80~150m)에 묶여 화면 숫자가 뚝뚝 끊긴다.
 * 그래서 선분 위로 수직 투영해 그 안쪽 진행분까지 더한다.
 * <p>
 * 남은 거리는 **비율로 환산해서** 돌려준다. 경로의 총거리(`totalDistance`)는 티맵이 준 값이고
 * 폴리라인을 실제로 이어 붙인 길이와 일치하지 않는다. 특히 대중교통은
 * `TransitSummaryResponse.totalDistance`가 leg distance 합과 어긋난다고 명시돼 있다.
 * 빼기로 계산하면 도착 직전에 음수가 나오거나 남은 거리가 총거리를 넘는다.
 */
@Component
public class RouteProgressCalculator {

    /** 경로에서 이만큼 떨어져 있으면 매칭이 의미 없다고 본다. */
    private static final double OFF_ROUTE_THRESHOLD_M = 200.0;

    private static final double LAT_DEGREE_M = 110_574.0;
    private static final double LON_DEGREE_M = 111_320.0;

    /** 폴리라인 한 점. `base`는 이 점이 속한 구간의 시작 누적거리다. */
    private record Vertex(double lat, double lon, double cumulative) {}

    /**
     * 남은 거리(m). 구할 수 없으면 null.
     *
     * @param totalDistanceM 화면에 총거리로 보여주는 값. 결과를 이 척도에 맞춘다
     */
    public Integer remainingDistance(NavigationRouteReport report, double lat, double lon, int totalDistanceM) {
        return remaining(walkVertices(report), lat, lon, totalDistanceM);
    }

    public Integer remainingDistance(TransitRoute route, double lat, double lon, int totalDistanceM) {
        return remaining(transitVertices(route), lat, lon, totalDistanceM);
    }

    private Integer remaining(List<Vertex> vertices, double lat, double lon, int totalDistanceM) {
        if (vertices.size() < 2 || totalDistanceM <= 0) return null;

        double routeLength = vertices.getLast().cumulative();
        if (routeLength <= 0) return null;

        double bestOffset = Double.MAX_VALUE;
        double traveled = 0;

        for (int i = 0; i < vertices.size() - 1; i++) {
            Vertex a = vertices.get(i);
            Vertex b = vertices.get(i + 1);

            // 선분 AB 위로 P를 투영한다. t는 A에서 B까지를 0~1로 본 위치
            double abX = eastM(a.lat(), b.lon() - a.lon());
            double abY = northM(b.lat() - a.lat());
            double apX = eastM(a.lat(), lon - a.lon());
            double apY = northM(lat - a.lat());

            double abLenSq = abX * abX + abY * abY;
            double t = abLenSq == 0 ? 0 : clamp((apX * abX + apY * abY) / abLenSq);

            double offsetX = apX - abX * t;
            double offsetY = apY - abY * t;
            double offset = Math.hypot(offsetX, offsetY);

            if (offset < bestOffset) {
                bestOffset = offset;
                traveled = a.cumulative() + (b.cumulative() - a.cumulative()) * t;
            }
        }

        // 경로에서 너무 멀면 어느 구간에 있는지 말할 수 없다. 틀린 숫자보다 빈 값이 낫다
        if (bestOffset > OFF_ROUTE_THRESHOLD_M) return null;

        double remainingRatio = 1 - (traveled / routeLength);
        return (int) Math.round(totalDistanceM * clamp(remainingRatio));
    }

    /**
     * 도보·자동차는 `RouteStep.cumulativeDistance`가 이미 있어서 그것을 구간 시작점으로 삼고,
     * 구간 안쪽은 `pathToNext` 폴리라인 길이로 채운다.
     */
    private List<Vertex> walkVertices(NavigationRouteReport report) {
        List<Vertex> vertices = new ArrayList<>();
        if (report == null || report.report() == null) return vertices;

        for (RouteStep step : report.report()) {
            double base = step.cumulativeDistance() == null ? lastCumulative(vertices) : step.cumulativeDistance();
            appendPath(vertices, step.pathToNext(), base);
        }
        return vertices;
    }

    /**
     * 대중교통은 누적거리가 없어서 leg를 순서대로 이어 붙이며 직접 쌓는다.
     * 좌표는 leg의 `path`에 있고, 도보 leg만 비어 있어 `steps`에서 꺼낸다.
     */
    private List<Vertex> transitVertices(TransitRoute route) {
        List<Vertex> vertices = new ArrayList<>();
        if (route == null || route.legs() == null) return vertices;

        double base = 0;
        for (TransitRoute.TransitLeg leg : route.legs()) {
            List<List<Double>> path = leg.path();
            if (path == null || path.isEmpty()) {
                path = new ArrayList<>();
                if (leg.steps() != null) {
                    for (TransitRoute.TransitStep step : leg.steps()) {
                        if (step.path() != null) path.addAll(step.path());
                    }
                }
            }
            appendPath(vertices, path, base);
            base = lastCumulative(vertices);
        }
        return vertices;
    }

    /** 좌표 목록을 누적거리와 함께 이어 붙인다. 이미 쌓인 마지막 점과 겹치면 건너뛴다. */
    private void appendPath(List<Vertex> vertices, List<List<Double>> path, double base) {
        if (path == null || path.isEmpty()) return;

        double cumulative = Math.max(base, lastCumulative(vertices));
        for (List<Double> point : path) {
            if (point == null || point.size() < 2) continue;
            double lat = point.get(0);
            double lon = point.get(1);

            if (!vertices.isEmpty()) {
                Vertex prev = vertices.getLast();
                if (prev.lat() == lat && prev.lon() == lon) continue;
                cumulative += distanceM(prev.lat(), prev.lon(), lat, lon);
            }
            vertices.add(new Vertex(lat, lon, cumulative));
        }
    }

    private double lastCumulative(List<Vertex> vertices) {
        return vertices.isEmpty() ? 0 : vertices.getLast().cumulative();
    }

    /**
     * 한국 위도대에서 수 km 거리라 하버사인 대신 등거리 평면 근사를 쓴다.
     * 오차가 0.1% 미만이고, 선분 투영을 평면에서 해야 계산이 단순하다.
     */
    private double distanceM(double lat1, double lon1, double lat2, double lon2) {
        return Math.hypot(eastM(lat1, lon2 - lon1), northM(lat2 - lat1));
    }

    private double eastM(double lat, double lonDelta) {
        return lonDelta * LON_DEGREE_M * Math.cos(Math.toRadians(lat));
    }

    private double northM(double latDelta) {
        return latDelta * LAT_DEGREE_M;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
