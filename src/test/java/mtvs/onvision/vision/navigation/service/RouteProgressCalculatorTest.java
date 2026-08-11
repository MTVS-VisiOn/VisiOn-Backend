package mtvs.onvision.vision.navigation.service;

import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.NavigationRouteReport;
import mtvs.onvision.vision.navigation.dto.RouteStep;
import mtvs.onvision.vision.navigation.dto.TransitRoute;
import mtvs.onvision.vision.navigation.dto.TransitSummaryResponse;
import mtvs.onvision.vision.navigation.dto.WalkSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 남은 거리 계산.
 *
 * 좌표는 서울 근처의 실제 위경도를 쓴다. 위도 1도 ≈ 110.5km, 경도 1도 ≈ 88km(위도 37도 기준)라
 * 위경도 증분을 미터로 환산하며 기대값을 잡았다.
 *
 * 경로는 위도만 증가하는 남북 직선이다. 동서 성분이 섞이면 기대값을 손으로 계산할 수 없다.
 */
@DisplayName("RouteProgressCalculator의")
class RouteProgressCalculatorTest {

    private final RouteProgressCalculator calculator = new RouteProgressCalculator();

    private static final double LON = 127.024798;
    private static final double START_LAT = 37.500000;

    /** 위도 0.001도 ≈ 110.574m. 구간 3개 = 약 331.7m 짜리 직선 경로를 만든다. */
    private static final double STEP_LAT = 0.001;

    private NavigationRouteReport walkReport(int totalDistanceM) {
        return new NavigationRouteReport(walkSummary(totalDistanceM),
                List.of(
                        step(0, 0, List.of(
                                List.of(START_LAT, LON),
                                List.of(START_LAT + STEP_LAT, LON))),
                        step(1, 111, List.of(
                                List.of(START_LAT + STEP_LAT, LON),
                                List.of(START_LAT + STEP_LAT * 2, LON))),
                        step(2, 221, List.of(
                                List.of(START_LAT + STEP_LAT * 2, LON),
                                List.of(START_LAT + STEP_LAT * 3, LON)))));
    }

    private WalkSummaryResponse walkSummary(int totalDistanceM) {
        return new WalkSummaryResponse(
                0, TransportMode.WALK, totalDistanceM, 2400, 0, 0, 0, 0,
                "출발", "출발 주소", List.of(START_LAT, LON),
                "도착", "도착 주소", List.of(START_LAT + STEP_LAT * 3, LON));
    }

    private RouteStep step(int sequence, int cumulative, List<List<Double>> path) {
        double lat = path.isEmpty() ? START_LAT : path.getFirst().get(0);
        double lon = path.isEmpty() ? LON : path.getFirst().get(1);
        return new RouteStep(sequence, lat, lon, "이동", null, "지점", null, 111, 80, cumulative, path);
    }

    @Nested
    @DisplayName("Describe: 도보·자동차 경로에서")
    class Describe_with_walk {

        @Test
        @DisplayName("It : 출발점에 있으면 총거리를 그대로 남긴다")
        void it_returns_total_at_start() {
            Integer remaining = calculator.remainingDistance(walkReport(1000), START_LAT, LON, 1000);

            assertThat(remaining).isEqualTo(1000);
        }

        @Test
        @DisplayName("It : 도착점에 있으면 0이다")
        void it_returns_zero_at_destination() {
            Integer remaining = calculator.remainingDistance(
                    walkReport(1000), START_LAT + STEP_LAT * 3, LON, 1000);

            assertThat(remaining).isZero();
        }

        @Test
        @DisplayName("It : 중간 지점에 있으면 절반쯤 남는다")
        void it_returns_half_at_midpoint() {
            Integer remaining = calculator.remainingDistance(
                    walkReport(1000), START_LAT + STEP_LAT * 1.5, LON, 1000);

            assertThat(remaining).isBetween(490, 510);
        }

        @Test
        @DisplayName("(꼭짓점이 아닌 자리)It : 선분 안쪽 진행분까지 반영한다")
        void it_projects_onto_segment() {
            // 두번째 구간의 1/4 지점. 꼭짓점만 비교하면 여기가 1/3(=333)으로 잘린다
            Integer atQuarter = calculator.remainingDistance(
                    walkReport(1200), START_LAT + STEP_LAT * 1.25, LON, 1200);
            Integer atVertex = calculator.remainingDistance(
                    walkReport(1200), START_LAT + STEP_LAT, LON, 1200);

            assertThat(atQuarter).isLessThan(atVertex);
            assertThat(atQuarter).isBetween(690, 710);
        }

        @Test
        @DisplayName("It : 경로 옆으로 조금 벗어나도 진행분은 유지된다")
        void it_tolerates_small_offset() {
            // 경도로 0.0005도 ≈ 44m 옆. 임계 200m 안이다
            Integer remaining = calculator.remainingDistance(
                    walkReport(1000), START_LAT + STEP_LAT * 1.5, LON + 0.0005, 1000);

            assertThat(remaining).isBetween(490, 510);
        }
    }

    @Nested
    @DisplayName("Describe: 값을 낼 수 없는 경우")
    class Describe_with_unavailable {

        @Test
        @DisplayName("(경로에서 200m 초과)It : null을 준다")
        void it_returns_null_when_off_route() {
            // 경도로 0.005도 ≈ 440m 옆
            Integer remaining = calculator.remainingDistance(
                    walkReport(1000), START_LAT + STEP_LAT * 1.5, LON + 0.005, 1000);

            assertThat(remaining).isNull();
        }

        @Test
        @DisplayName("(총거리가 0)It : null을 준다")
        void it_returns_null_when_total_is_zero() {
            assertThat(calculator.remainingDistance(walkReport(0), START_LAT, LON, 0)).isNull();
        }

        @Test
        @DisplayName("(좌표가 하나도 없음)It : null을 준다")
        void it_returns_null_without_path() {
            NavigationRouteReport empty = new NavigationRouteReport(
                    walkReport(1000).summary(), List.of(step(0, 0, List.of())));

            assertThat(calculator.remainingDistance(empty, START_LAT, LON, 1000)).isNull();
        }
    }

    @Nested
    @DisplayName("Describe: 대중교통 경로에서")
    class Describe_with_transit {

        /** leg 길이 합(300)과 totalDistance(1000)가 일부러 어긋나 있다. 실제 티맵 응답이 그렇다 */
        private TransitRoute transitRoute() {
            return new TransitRoute(
                    new TransitSummaryResponse(
                            0, TransportMode.TRANSIT, 1000, 953, 389, 461, 0, 3000,
                            List.of(),
                            "출발", "출발 주소", List.of(START_LAT, LON),
                            "도착", "도착 주소", List.of(START_LAT + STEP_LAT * 3, LON)),
                    List.of(
                            leg(0, "WALK", 100, List.of(), List.of(
                                    new TransitRoute.TransitStep(0, "이동", 100, "", List.of(
                                            List.of(START_LAT, LON),
                                            List.of(START_LAT + STEP_LAT, LON))))),
                            leg(1, "BUS", 200, List.of(
                                    List.of(START_LAT + STEP_LAT, LON),
                                    List.of(START_LAT + STEP_LAT * 3, LON)), List.of())));
        }

        private TransitRoute.TransitLeg leg(int sequence, String mode, int distance,
                                            List<List<Double>> path, List<TransitRoute.TransitStep> steps) {
            return new TransitRoute.TransitLeg(sequence, mode, null, null, null, null, List.of(),
                    120, distance, null, "시작", List.of(START_LAT, LON), "끝",
                    List.of(START_LAT + STEP_LAT, LON), List.of(), steps, path);
        }

        @Test
        @DisplayName("(도보 leg은 path가 비어 steps에 좌표가 있다)It : 두 자리를 모두 읽는다")
        void it_reads_path_from_both_places() {
            Integer atStart = calculator.remainingDistance(transitRoute(), START_LAT, LON, 1000);
            Integer atEnd = calculator.remainingDistance(
                    transitRoute(), START_LAT + STEP_LAT * 3, LON, 1000);

            assertThat(atStart).isEqualTo(1000);
            assertThat(atEnd).isZero();
        }

        @Test
        @DisplayName("(leg 합 300 ≠ totalDistance 1000)It : 비율로 환산해 총거리를 넘지 않는다")
        void it_scales_to_total_distance() {
            Integer remaining = calculator.remainingDistance(
                    transitRoute(), START_LAT + STEP_LAT * 1.5, LON, 1000);

            // 빼기로 계산하면 1000 - 165 = 835처럼 척도가 어긋난 값이 나온다
            assertThat(remaining).isBetween(0, 1000);
            assertThat(remaining).isBetween(490, 510);
        }
    }
}
