package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis·DB에 저장하고 조회 응답에 그대로 실어 보내는 경로 원문.
 *
 * `requestedStart`는 티맵에 startX/startY로 보낸 좌표, `snappedStart`는 티맵이 보행로 위로
 * 옮겨 돌려준 첫 안내점 좌표다. 둘이 벌어진 거리(`snapDistanceM`)가 크면 안내가 실제 서 있는
 * 자리가 아닌 곳에서 시작한다는 뜻이라, 클라이언트가 판단할 수 있게 같이 내보낸다.
 *
 * `requestedEnd`는 endX/endY로 보낸 **실제 목적지 좌표**다. 보행자 입구점이 있으면 중심점이 아니라
 * 그쪽으로 가므로, 요약의 `destinationCoordinate`(장소의 중심점)와 다를 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NavigationRouteReport(
        NavigationSummary summary,
        List<Double> requestedStart,   // [위도, 경도]. 티맵에 보낸 출발 좌표
        List<Double> snappedStart,     // [위도, 경도]. 티맵이 보정한 출발 좌표
        Double snapDistanceM,          // 두 좌표 사이 거리(m)
        List<Double> requestedEnd,     // [위도, 경도]. 티맵에 보낸 목적지 좌표
        List<RouteStep> report
) {
    /** 보정 좌표가 없는 호출부(테스트 픽스처 등)용. */
    public NavigationRouteReport(NavigationSummary summary, List<RouteStep> report) {
        this(summary, null, null, null, null, report);
    }

    /**
     * 경로 전체 폴리라인. `report`의 `pathToNext`를 순서대로 이은 것이라 저장하지 않고 매번 만든다.
     * 저장하면 같은 좌표가 두 벌이 되고, 한쪽만 고쳐지면 조용히 어긋난다.
     *
     * 구간 경계에서는 앞 step의 마지막 좌표와 다음 step의 첫 좌표가 같은 안내점이라 한 번 건너뛴다.
     */
    @JsonProperty(value = "routePath", access = JsonProperty.Access.READ_ONLY)
    public List<List<Double>> routePath() {
        if (report == null) return List.of();
        List<List<Double>> path = new ArrayList<>();
        for (RouteStep step : report) {
            if (step == null || step.pathToNext() == null) continue;
            for (List<Double> point : step.pathToNext()) {
                if (!path.isEmpty() && path.getLast().equals(point)) continue;
                path.add(point);
            }
        }
        return path;
    }
}
