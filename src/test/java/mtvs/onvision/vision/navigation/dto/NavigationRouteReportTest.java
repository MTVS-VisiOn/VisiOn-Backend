package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NavigationRouteReport의")
class NavigationRouteReportTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private WalkSummaryResponse walkSummary() {
        return new WalkSummaryResponse(
                0, TransportMode.WALK,
                3103, 2400, 11, 0, 0, 0,
                "신논현역", "서울 강남구 강남대로 지하 476", List.of(37.504585, 127.024798),
                "말죽거리공원사거리", "서울 서초구 강남대로 213", List.of(37.479103, 127.037476));
    }

    private RouteStep step(int sequence, List<List<Double>> pathToNext) {
        return new RouteStep(sequence, 37.504585, 127.024798, "160m 이동",
                200, "출발지", "일반보행자도로", 160, 118, 0, pathToNext);
    }

    @Nested
    @DisplayName("Describe: routePath 메서드는")
    class Describe_with_routePath {

        @Nested
        @DisplayName("Context: step이 여러 개면")
        class Context_with_steps {

            @Test
            @DisplayName("It : pathToNext를 순서대로 이어 하나의 폴리라인으로 만든다")
            void it_joins_paths_in_order() {
                //given
                NavigationRouteReport report = new NavigationRouteReport(walkSummary(), List.of(
                        step(0, List.of(List.of(37.5046, 127.0248), List.of(37.5044, 127.0242))),
                        step(1, List.of(List.of(37.5038, 127.0245), List.of(37.5035, 127.0246)))));

                //when&then : 순서가 뒤집히면 경로가 되돌아간다
                assertThat(report.routePath()).containsExactly(
                        List.of(37.5046, 127.0248), List.of(37.5044, 127.0242),
                        List.of(37.5038, 127.0245), List.of(37.5035, 127.0246));
            }

            @Test
            @DisplayName("(구간 경계)It : 앞 step의 끝과 다음 step의 시작이 같으면 한 번만 담는다")
            void it_skips_duplicated_boundary() {
                //given : 티맵 LineString의 마지막 좌표 = 다음 안내점 좌표라 경계에서 반드시 겹친다
                List<Double> boundary = List.of(37.5044, 127.0242);
                NavigationRouteReport report = new NavigationRouteReport(walkSummary(), List.of(
                        step(0, List.of(List.of(37.5046, 127.0248), boundary)),
                        step(1, List.of(boundary, List.of(37.5038, 127.0245)))));

                //when&then
                assertThat(report.routePath()).containsExactly(
                        List.of(37.5046, 127.0248), boundary, List.of(37.5038, 127.0245));
            }
        }

        @Nested
        @DisplayName("Context: 좌표가 없는 step이 섞여 있으면")
        class Context_with_empty_step {

            @Test
            @DisplayName("It : 그 step만 건너뛰고 나머지를 잇는다")
            void it_skips_empty_step() {
                //given : 마지막 step(EP)은 뒤 구간이 없어 비는 것이 정상이다. null은 옛 데이터 방어
                NavigationRouteReport report = new NavigationRouteReport(walkSummary(), Arrays.asList(
                        step(0, List.of(List.of(37.5046, 127.0248), List.of(37.5044, 127.0242))),
                        step(1, null),
                        step(2, List.of())));

                //when&then
                assertThat(report.routePath())
                        .containsExactly(List.of(37.5046, 127.0248), List.of(37.5044, 127.0242));
            }
        }
    }

    @Nested
    @DisplayName("Describe: JSON 왕복은")
    class Describe_with_json {

        /**
         * 검색 응답은 Redis에 문자열로 들어갔다가 경로 선택 시점에 다시 읽힌다.
         * routePath는 저장하지 않고 만들어 내보내는 값이라, 되읽을 때 모르는 필드로 걸리면 그 자리가 깨진다.
         */
        @Test
        @DisplayName("It : routePath를 내보내고, 그 JSON을 다시 읽어도 깨지지 않는다")
        void it_survives_round_trip() {
            //given
            NavigationRouteReport report = new NavigationRouteReport(
                    walkSummary(), List.of(37.5046, 127.0247), List.of(37.504585, 127.024798), 4.6,
                    List.of(37.479103, 127.037476),
                    List.of(step(0, List.of(List.of(37.5046, 127.0248), List.of(37.5044, 127.0242)))));

            //when
            String json = objectMapper.writeValueAsString(report);
            NavigationRouteReport parsed = objectMapper.readValue(json, NavigationRouteReport.class);

            //then
            assertThat(json).contains("\"routePath\"");
            assertThat(parsed.requestedStart()).containsExactly(37.5046, 127.0247);
            assertThat(parsed.snappedStart()).containsExactly(37.504585, 127.024798);
            assertThat(parsed.snapDistanceM()).isEqualTo(4.6);
            assertThat(parsed.requestedEnd()).containsExactly(37.479103, 127.037476);
            assertThat(parsed.routePath()).isEqualTo(report.routePath());
        }
    }
}
