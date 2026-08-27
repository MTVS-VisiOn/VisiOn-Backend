package mtvs.onvision.vision.navigation.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.config.properties.NavigationStartProperties;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import mtvs.onvision.vision.navigation.domain.Route;
import mtvs.onvision.vision.navigation.domain.RouteStatus;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.*;
import mtvs.onvision.vision.navigation.repository.NavigationRepository;
import mtvs.onvision.vision.navigation.repository.RouteRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static mtvs.onvision.vision.alert.service.AlertService.SEOUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("NavigationService의")
class NavigationServiceTest {

    @InjectMocks
    private NavigationService navigationService;

    @Mock
    private RestClient tmapRestClient;

    @Mock
    private NavigationRepository navigationRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private UserService userService;

    @Mock
    private RealtimeLocationRepository realtimeLocationRepository;

    @Mock
    private RouteProgressCalculator routeProgressCalculator;

    /**
     * 진짜 값을 쓴다. 목으로 대체하면 문턱이 전부 0이 돼 어떤 좌표도 통과하지 못하고
     * 출발 좌표 관련 테스트가 통째로 409로 떨어진다.
     * 값은 {@code application.yml}의 기본값과 같게 둔다.
     */
    @Spy
    private NavigationStartProperties startPolicy = new NavigationStartProperties(
            30f,
            Duration.ofSeconds(30),
            Duration.ofSeconds(180),
            Duration.ofSeconds(60),
            Duration.ofSeconds(5));

    /**
     * 진짜 매퍼를 쓴다. Redis의 report를 다시 읽는 게 이 도메인에서 가장 조용히 깨지는 자리라
     * (@JsonTypeInfo로 NavigationSummary 구현체를 되찾는 경로) 목으로 대체하면 의미가 없다.
     */
    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * 픽스처 JSON은 이쪽으로 만든다. 스파이를 쓰면 given(...) 괄호 안에서 목을 건드리게 되고,
     * Mockito가 그 호출을 스터빙 시도로 오해해 UnfinishedStubbingException을 던진다.
     */
    private final ObjectMapper fixtureMapper = JsonMapper.builder().build();

    Long wardId = 2L;
    Long guardianId = 1L;

    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);
    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);

    User wardEntity = new User("ward@test.com", "password", "피보호자", "01012345678", UserRole.WARD);

    /**
     * 좌표는 [위도, 경도]. 한국은 위도 33~38 · 경도 124~132라 뒤집히면 값만 봐도 보인다.
     * 이 순서가 Route의 lat/lon 컬럼까지 그대로 가는지가 아래 검증의 핵심이다.
     */
    private WalkSummaryResponse walkSummary() {
        return new WalkSummaryResponse(
                0, TransportMode.WALK,
                24269, 21600,
                44, 0, 1, 0,
                "신논현역", "서울 강남구 강남대로 지하 476", List.of(37.504585, 127.024798),
                "말죽거리공원사거리", "서울 서초구 강남대로 213", List.of(37.479103, 127.037476));
    }

    private TransitSummaryResponse transitSummary(int index) {
        return new TransitSummaryResponse(
                index, TransportMode.TRANSIT,
                3278, 953, 389, 461, 0, 3000,
                List.of(new TransitSummaryResponse.LegSummary(
                        "버스", "광역:9711", List.of("광역:9711"),
                        "신논현역.(구)교보타워사거리", "교육개발원입구", 6, 564, 2955)),
                "신논현역", "서울 강남구 강남대로 지하 476", List.of(37.504585, 127.024798),
                "말죽거리공원사거리", "서울 서초구 강남대로 213", List.of(37.479103, 127.037476));
    }

    /** legs는 비워둔다. 파싱은 실호출(신논현→말죽거리)로 이미 덮였고 여기서 볼 건 후보 선택이다 */
    private TransitRoute transitRoute(int index) {
        return new TransitRoute(transitSummary(index), List.of());
    }

    private String walkReportJson() {
        return fixtureMapper.writeValueAsString(new NavigationRouteReport(
                walkSummary(),
                List.of(new RouteStep(0, 37.504585, 127.024798, "55m 이동",
                        null, "출발지", null, 55, 40, 0,
                        List.of(List.of(37.504562, 127.024810))))));
    }

    /** 후보 배열의 물리적 순서를 일부러 뒤섞는다. index가 위치가 아님을 드러내기 위해서다 */
    private String transitCandidatesJson() {
        return fixtureMapper.writeValueAsString(
                List.of(transitRoute(2), transitRoute(0), transitRoute(1)));
    }

    private Route savedRoute() {
        ArgumentCaptor<Route> captor = ArgumentCaptor.forClass(Route.class);
        verify(routeRepository).save(captor.capture());
        return captor.getValue();
    }

    private Route existingRoute() {
        return new Route(TransportMode.WALK, walkSummary(), walkReportJson(), wardEntity);
    }

    @Nested
    @DisplayName("Describe: searchNavigation 메서드는")
    class Describe_with_searchNavigation {

        private NavigationPreRequest requestWith(Float accuracy, Instant recordedAt) {
            LocationInfo start = new LocationInfo("현재 위치", "현재 위치", 37.504585, 127.024798,
                    "서울 강남구 강남대로 지하 476", null);
            LocationInfo end = new LocationInfo("말죽거리공원사거리", "말죽거리공원사거리", 37.479103, 127.037476,
                    "서울 서초구 강남대로 213", null);
            return new NavigationPreRequest(TransportMode.WALK, start, end, "sample-1", accuracy, recordedAt);
        }

        @Nested
        @DisplayName("Context: 요청 좌표도 저장 좌표도 못 믿을 때")
        class Context_with_low_confidence {

            @Test
            @DisplayName("It : 티맵을 부르지 않고 LOW_CONFIDENCE_LOCATION을 던진다")
            void it_throws_low_confidence() {
                //given — 정확도 100m는 문턱(30m) 밖이고, 저장된 위치도 없다
                NavigationPreRequest request = requestWith(100f, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when&then
                BusinessException thrown = assertThrows(BusinessException.class,
                        () -> navigationService.searchNavigation(request, ward));
                assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.LOW_CONFIDENCE_LOCATION);
                verifyNoInteractions(tmapRestClient);
            }

            @Test
            @DisplayName("It : 정확도가 없어도(null) 신뢰하지 않는다")
            void it_rejects_null_accuracy() {
                //given
                NavigationPreRequest request = requestWith(null, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when&then
                assertThrows(BusinessException.class, () -> navigationService.searchNavigation(request, ward));
            }

            @Test
            @DisplayName("It : 측정 시각이 요청 좌표 수명을 넘으면 신뢰하지 않는다")
            void it_rejects_stale_request_coordinate() {
                //given — 정확도는 좋지만 오래됐다. 60초는 어떤 기본값(현재 30초)보다도 길다
                NavigationPreRequest request = requestWith(5f, Instant.now().minusSeconds(60));
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when&then
                assertThrows(BusinessException.class, () -> navigationService.searchNavigation(request, ward));
            }
        }
    }


    @Nested
    @DisplayName("Describe: saveRoute 메서드는")
    class Describe_with_saveRoute {

        @Nested
        @DisplayName("Context: 보행자 경로를 고르면")
        class Context_with_walk {

            @Test
            @DisplayName("It : Redis의 report 원문을 그대로 저장한다")
            void it_stores_raw_report() {
                //given
                String json = walkReportJson();
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.WALK.getPrefix()))
                        .willReturn(Optional.of(json));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.WALK, null), ward);

                //then : 재직렬화하지 않는다. 문자열이 그대로 컬럼에 들어간다
                assertThat(savedRoute().getReport()).isEqualTo(json);
            }

            @Test
            @DisplayName("(위도 37 · 경도 127)It : 요약 좌표의 앞이 위도, 뒤가 경도로 들어간다")
            void it_maps_coordinate_in_lat_lon_order() {
                //given
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.WALK.getPrefix()))
                        .willReturn(Optional.of(walkReportJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.WALK, null), ward);

                //then
                Route saved = savedRoute();
                assertThat(saved.getStartingLat()).isEqualTo(37.504585);
                assertThat(saved.getStartingLon()).isEqualTo(127.024798);
                assertThat(saved.getDestinationLat()).isEqualTo(37.479103);
                assertThat(saved.getDestinationLon()).isEqualTo(127.037476);
            }

            @Test
            @DisplayName("It : 상태를 IN_PROGRESS로, mode를 요청값으로 저장한다")
            void it_starts_in_progress() {
                //given
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.WALK.getPrefix()))
                        .willReturn(Optional.of(walkReportJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.WALK, null), ward);

                //then
                Route saved = savedRoute();
                assertThat(saved.getStatus()).isEqualTo(RouteStatus.IN_PROGRESS);
                assertThat(saved.getMode()).isEqualTo(TransportMode.WALK);
                assertThat(saved.getWard()).isSameAs(wardEntity);
                assertThat(saved.getTotalDistance()).isEqualTo(24269);
                assertThat(saved.getTotalTime()).isEqualTo(21600);
            }

            @Test
            @DisplayName("(mode별로 키가 갈린다)It : 요청 mode의 Redis 키만 읽는다")
            void it_reads_prefix_of_requested_mode() {
                //given : WALK로 찾고 CAR로 다시 찾으면 두 키가 다 남아 있다
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.CAR.getPrefix()))
                        .willReturn(Optional.of(walkReportJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.CAR, null), ward);

                //then
                verify(navigationRepository).getRoute(wardId, "car:");
                verify(navigationRepository, never()).getRoute(wardId, "walk:");
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 이미 있으면")
        class Context_with_existing_route {

            @Test
            @DisplayName("It : 기존 경로를 CANCELED로 바꾸고 새 경로를 저장한다")
            void it_cancels_previous_route() {
                //given
                Route previous = existingRoute();
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(previous));
                given(navigationRepository.getRoute(wardId, TransportMode.WALK.getPrefix()))
                        .willReturn(Optional.of(walkReportJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.WALK, null), ward);

                //then : 더티체킹으로 상태만 바꾼다. save를 다시 부르지 않는다
                assertThat(previous.getStatus()).isEqualTo(RouteStatus.CANCELED);
                assertThat(savedRoute().getStatus()).isEqualTo(RouteStatus.IN_PROGRESS);
            }
        }

        @Nested
        @DisplayName("Context: Redis에 후보가 없으면 (TTL 30분 만료)")
        class Context_with_expired_candidates {

            @Test
            @DisplayName("It : NOT_FOUND_ROUTE 오류 발생")
            void it_throws_not_found_route() {
                //given
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.WALK.getPrefix()))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> navigationService.saveRoute(new RouteRequest(TransportMode.WALK, null), ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ROUTE);
            }

            @Test
            @DisplayName("It : 저장을 시도하지 않는다")
            void it_does_not_save() {
                //given : 기존 경로 취소가 롤백되는지는 여기서 못 본다. @Transactional은 단위 테스트에서 안 돈다
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.WALK.getPrefix()))
                        .willReturn(Optional.empty());

                //when
                assertThrows(BusinessException.class,
                        () -> navigationService.saveRoute(new RouteRequest(TransportMode.WALK, null), ward));

                //then
                verify(routeRepository, never()).save(any());
            }
        }

        @Nested
        @DisplayName("Context: 대중교통 후보를 고르면")
        class Context_with_transit {

            @Test
            @DisplayName("(배열 위치가 아니라)It : summary.index가 일치하는 후보를 고른다")
            void it_selects_by_index_value_not_position() {
                //given : 후보 배열은 [2, 0, 1] 순서다. 위치로 꺼내면 index 0을 집게 된다
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.TRANSIT.getPrefix()))
                        .willReturn(Optional.of(transitCandidatesJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.TRANSIT, 1), ward);

                //then
                TransitRoute stored = fixtureMapper.readValue(savedRoute().getReport(), TransitRoute.class);
                assertThat(stored.summary().index()).isEqualTo(1);
            }

            @Test
            @DisplayName("It : 후보 배열이 아니라 고른 후보 하나만 report에 담는다")
            void it_stores_single_candidate() {
                //given
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.TRANSIT.getPrefix()))
                        .willReturn(Optional.of(transitCandidatesJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.TRANSIT, 0), ward);

                //then : 배열이면 '['로 시작한다
                assertThat(savedRoute().getReport()).startsWith("{");
                assertThat(savedRoute().getMode()).isEqualTo(TransportMode.TRANSIT);
            }

            @Test
            @DisplayName("It : 대중교통 요약의 좌표도 위도·경도 순서를 지킨다")
            void it_maps_transit_coordinate_in_lat_lon_order() {
                //given
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.TRANSIT.getPrefix()))
                        .willReturn(Optional.of(transitCandidatesJson()));

                //when
                navigationService.saveRoute(new RouteRequest(TransportMode.TRANSIT, 0), ward);

                //then
                assertThat(savedRoute().getStartingLat()).isEqualTo(37.504585);
                assertThat(savedRoute().getStartingLon()).isEqualTo(127.024798);
            }
        }

        @Nested
        @DisplayName("Context: 요청 index가 후보에 없으면")
        class Context_with_unknown_index {

            @Test
            @DisplayName("It : NOT_FOUND_ROUTE 오류 발생")
            void it_throws_not_found_route() {
                //given
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());
                given(navigationRepository.getRoute(wardId, TransportMode.TRANSIT.getPrefix()))
                        .willReturn(Optional.of(transitCandidatesJson()));

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> navigationService.saveRoute(new RouteRequest(TransportMode.TRANSIT, 99), ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ROUTE);
                verify(routeRepository, never()).save(any());
            }
        }
    }

    @Nested
    @DisplayName("Describe: getProcessingRoute 메서드는")
    class Describe_with_getProcessingRoute {

        @Nested
        @DisplayName("Context: 호출자가 WARD면")
        class Context_with_ward {

            @Test
            @DisplayName("It : 피보호자 조회를 거치지 않고 본인 id를 쓴다")
            void it_uses_own_id() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(existingRoute()));

                //when
                navigationService.getProcessingRoute(ward);

                //then
                verify(userService, never()).getWardIdFromGuardianId(anyLong());
            }
        }

        @Nested
        @DisplayName("Context: 호출자가 GUARDIAN이면")
        class Context_with_guardian {

            @Test
            @DisplayName("It : 본인이 아니라 피보호자의 경로를 조회한다")
            void it_reads_ward_route() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(existingRoute()));

                //when
                navigationService.getProcessingRoute(guardian);

                //then
                verify(routeRepository).findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS);
                verify(routeRepository, never()).findByWardIdAndStatus(guardianId, RouteStatus.IN_PROGRESS);
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : report를 재직렬화하지 않고 원문 그대로 넘긴다")
            void it_passes_raw_report() {
                //given
                Route route = existingRoute();
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route));

                //when
                NavigationResponse response = navigationService.getProcessingRoute(ward);

                //then
                assertThat(response.report()).isEqualTo(route.getReport());
                assertThat(response.mode()).isEqualTo(TransportMode.WALK);
            }

            @Test
            @DisplayName("It : 컬럼의 lat/lon을 LocationInfo의 latitude/longitude 자리에 맞춘다")
            void it_maps_columns_to_location_info() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(existingRoute()));

                //when
                NavigationResponse response = navigationService.getProcessingRoute(ward);

                //then
                assertThat(response.start().latitude()).isEqualTo(37.504585);
                assertThat(response.start().longitude()).isEqualTo(127.024798);
                assertThat(response.start().address()).isEqualTo("서울 강남구 강남대로 지하 476");
                assertThat(response.end().latitude()).isEqualTo(37.479103);
                assertThat(response.end().longitude()).isEqualTo(127.037476);
            }

            @Test
            @DisplayName("(피보호자 위치가 없을때)It : remainingDistanceM이 null이고 report를 파싱하지 않는다")
            void it_skips_calculation_without_location() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(existingRoute()));
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when
                NavigationResponse response = navigationService.getProcessingRoute(ward);

                //then : 안내 중 반복 호출되는 API라 위치가 없으면 JSON 파싱까지 건너뛴다
                assertThat(response.remainingDistanceM()).isNull();
                verifyNoInteractions(routeProgressCalculator);
            }

            @Test
            @DisplayName("(피보호자 위치가 있을때)It : 계산기 결과를 remainingDistanceM에 싣는다")
            void it_carries_remaining_distance() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(existingRoute()));
                LocationReport location = new LocationReport(
                        wardId, 37.503900, 127.025200, 5.0f, MovementStatus.ON_FOOT, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId))
                        .willReturn(Optional.of(fixtureMapper.writeValueAsString(location)));
                given(routeProgressCalculator.remainingDistance(
                        any(NavigationRouteReport.class), eq(37.503900), eq(127.025200), anyInt()))
                        .willReturn(1830);

                //when
                NavigationResponse response = navigationService.getProcessingRoute(ward);

                //then
                assertThat(response.remainingDistanceM()).isEqualTo(1830);
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : NOT_FOUND_ROUTE 오류 발생")
            void it_throws_not_found_route() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> navigationService.getProcessingRoute(ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ROUTE);
            }
        }
    }

    @Nested
    @DisplayName("Describe: completeRoute 메서드는")
    class Describe_with_completeRoute {

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : 행을 다시 저장하지 않고 상태만 COMPLETED로 바꾼다")
            void it_completes_without_save() {
                //given
                Route route = existingRoute();
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route));

                //when
                navigationService.completeRoute(ward);

                //then
                assertThat(route.getStatus()).isEqualTo(RouteStatus.COMPLETED);
                verify(routeRepository, never()).save(any());
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : NOT_FOUND_ROUTE 오류 발생")
            void it_throws_not_found_route() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> navigationService.completeRoute(ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ROUTE);
            }
        }
    }

    @Nested
    @DisplayName("Describe: cancelRoute 메서드는")
    class Describe_with_cancelRoute {

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : 행을 지우지 않고 상태만 CANCELED로 바꾼다")
            void it_cancels_without_delete() {
                //given
                Route route = existingRoute();
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route));

                //when
                navigationService.cancelRoute(ward);

                //then
                assertThat(route.getStatus()).isEqualTo(RouteStatus.CANCELED);
                verify(routeRepository, never()).delete(any());
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : NOT_FOUND_ROUTE 오류 발생")
            void it_throws_not_found_route() {
                //given
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> navigationService.cancelRoute(ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ROUTE);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getMapRoute 메서드는")
    class Describe_with_getMapRoute {

        /** 감사 리스너가 안 도는 단위 테스트라 createdAt을 직접 넣어야 한다. null이면 시간대 변환에서 NPE다 */
        private final LocalDateTime departedAt = LocalDateTime.of(2026, 8, 3, 16, 8);

        /** step 두 개에 좌표 두 개씩. 이어붙인 결과가 4개여야 flatMap이 제대로 편 것이다 */
        private String walkReportJsonWithPath() {
            return fixtureMapper.writeValueAsString(new NavigationRouteReport(
                    walkSummary(),
                    List.of(
                            new RouteStep(0, 37.504585, 127.024798, "55m 이동",
                                    null, "출발지", null, 55, 40, 0,
                                    List.of(List.of(37.504585, 127.024798), List.of(37.504562, 127.024810))),
                            new RouteStep(1, 37.504100, 127.025000, "직진",
                                    null, "일반 안내점", null, 60, 45, 55,
                                    List.of(List.of(37.504100, 127.025000), List.of(37.503900, 127.025200))))));
        }

        /** 도보 leg : passShape이 없어 path가 비고, 좌표는 steps 안에 있다 */
        private TransitRoute.TransitLeg walkLeg() {
            return new TransitRoute.TransitLeg(
                    0, "WALK", null, null, null, null, List.of(), 120, 100, null,
                    "신논현역", List.of(37.504585, 127.024798),
                    "신논현역.(구)교보타워사거리", List.of(37.504000, 127.025000),
                    List.of(),
                    List.of(new TransitRoute.TransitStep(0, "직진", 100, "강남대로",
                            List.of(List.of(37.504585, 127.024798), List.of(37.504300, 127.024900)))),
                    List.of());
        }

        /** 대중교통 leg : path에 좌표가 있고 steps는 비어 있다 */
        private TransitRoute.TransitLeg busLeg() {
            return new TransitRoute.TransitLeg(
                    1, "BUS", "광역:9711", "1234", 11, "0068B7", List.of("광역:9711"), 564, 2955, 2900,
                    "신논현역.(구)교보타워사거리", List.of(37.504000, 127.025000),
                    "교육개발원입구", List.of(37.479103, 127.037476),
                    List.of(), List.of(),
                    List.of(List.of(37.504000, 127.025000), List.of(37.479103, 127.037476)));
        }

        private String transitReportJson() {
            return fixtureMapper.writeValueAsString(
                    new TransitRoute(transitSummary(0), List.of(walkLeg(), busLeg())));
        }

        private Route route(TransportMode mode, NavigationSummary summary, String json) {
            Route route = new Route(mode, summary, json, wardEntity);
            ReflectionTestUtils.setField(route, "createdAt", departedAt);
            return route;
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 예외가 아니라 null을 반환한다")
            void it_returns_null() {
                //given : 목적지 미설정은 오류가 아니다. 프론트가 data:null을 '정보 없음'으로 읽는다
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.empty());

                //when
                MapResponse response = navigationService.getMapRoute(guardian);

                //then
                assertThat(response).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 호출자가 WARD면")
        class Context_with_ward {

            @Test
            @DisplayName("It : 피보호자 조회를 거치지 않고 본인 id를 쓴다")
            void it_uses_own_id() {
                //given : DEVICE 토큰도 role은 WARD라 같은 분기를 탄다
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route(TransportMode.WALK, walkSummary(), walkReportJsonWithPath())));

                //when
                navigationService.getMapRoute(ward);

                //then
                verify(userService, never()).getWardIdFromGuardianId(anyLong());
            }
        }

        @Nested
        @DisplayName("Context: 호출자가 GUARDIAN이면")
        class Context_with_guardian {

            @Test
            @DisplayName("It : 본인이 아니라 피보호자의 경로를 조회한다")
            void it_reads_ward_route() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route(TransportMode.WALK, walkSummary(), walkReportJsonWithPath())));

                //when
                navigationService.getMapRoute(guardian);

                //then
                verify(routeRepository).findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS);
                verify(routeRepository, never()).findByWardIdAndStatus(guardianId, RouteStatus.IN_PROGRESS);
            }
        }

        @Nested
        @DisplayName("Context: 남은 거리는")
        class Context_with_remaining_distance {

            private void givenRoute() {
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route(TransportMode.WALK, walkSummary(), walkReportJsonWithPath())));
            }

            @Test
            @DisplayName("(피보호자 위치가 없을때)It : null이고 계산기를 부르지 않는다")
            void it_is_null_without_location() {
                //given : 앱이 꺼져 있거나 마지막 좌표가 Redis TTL을 넘긴 상태
                givenRoute();
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when
                MapResponse response = navigationService.getMapRoute(guardian);

                //then : 총거리로 대신 채우면 프론트가 진짜 값과 구분할 수 없다
                assertThat(response.remainingDistanceM()).isNull();
                assertThat(response.distanceM()).isEqualTo(24269);
                verifyNoInteractions(routeProgressCalculator);
            }

            @Test
            @DisplayName("(피보호자 위치가 있을때)It : 계산기 결과를 그대로 싣는다")
            void it_carries_calculated_value() {
                //given
                givenRoute();
                LocationReport location = new LocationReport(
                        wardId, 37.503900, 127.025200, 5.0f, MovementStatus.ON_FOOT, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId))
                        .willReturn(Optional.of(fixtureMapper.writeValueAsString(location)));
                given(routeProgressCalculator.remainingDistance(
                        any(NavigationRouteReport.class), eq(37.503900), eq(127.025200), eq(24269)))
                        .willReturn(2450);

                //when
                MapResponse response = navigationService.getMapRoute(guardian);

                //then
                assertThat(response.remainingDistanceM()).isEqualTo(2450);
            }

            @Test
            @DisplayName("(경로를 벗어나 계산기가 null을 줄때)It : null을 그대로 내보낸다")
            void it_passes_null_through() {
                //given
                givenRoute();
                LocationReport location = new LocationReport(
                        wardId, 37.600000, 127.100000, 5.0f, MovementStatus.ON_FOOT, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId))
                        .willReturn(Optional.of(fixtureMapper.writeValueAsString(location)));
                given(routeProgressCalculator.remainingDistance(
                        any(NavigationRouteReport.class), anyDouble(), anyDouble(), anyInt()))
                        .willReturn(null);

                //when&then
                assertThat(navigationService.getMapRoute(guardian).remainingDistanceM()).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 보행 경로가 진행 중이면")
        class Context_with_walk_route {

            private MapResponse call() {
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route(TransportMode.WALK, walkSummary(), walkReportJsonWithPath())));
                return navigationService.getMapRoute(guardian);
            }

            @Test
            @DisplayName("It : 목적지 정보를 내보낸다")
            void it_returns_destination() {
                //when
                MapResponse response = call();

                //then
                assertThat(response.name()).isEqualTo("말죽거리공원사거리");
                assertThat(response.address()).isEqualTo("서울 서초구 강남대로 213");
                assertThat(response.latitude()).isEqualTo(37.479103);
                assertThat(response.longitude()).isEqualTo(127.037476);
                assertThat(response.mode()).isEqualTo(TransportMode.WALK);
            }

            @Test
            @DisplayName("(초가 아니라 분)It : totalTime을 60으로 나눠 etaMin으로 내보낸다")
            void it_converts_seconds_to_minutes() {
                //when : TMap totalTime은 초 단위다. 그대로 내보내면 21600분이 된다
                MapResponse response = call();

                //then
                assertThat(response.distanceM()).isEqualTo(24269);
                assertThat(response.etaMin()).isEqualTo(360);
            }

            @Test
            @DisplayName("(안내 지점이 아니라 도로)It : step의 pathToNext를 전부 이어붙인다")
            void it_flattens_path_to_next() {
                //when : RouteStep의 latitude/longitude만 쓰면 턴 지점을 직선으로 이은 선이 된다
                MapResponse response = call();

                //then
                assertThat(response.path()).hasSize(4);
                assertThat(response.path().getFirst())
                        .containsEntry("latitude", 37.504585)
                        .containsEntry("longitude", 127.024798);
                assertThat(response.path().getLast())
                        .containsEntry("latitude", 37.503900)
                        .containsEntry("longitude", 127.025200);
            }

            @Test
            @DisplayName("It : createdAt을 KST로 해석해 Instant로 내보낸다")
            void it_converts_created_at_to_instant() {
                //when : createdAt은 시간대 정보가 없는 LocalDateTime이고, 서버가 KST로 기록한 값이다.
                // 시스템 시간대로 해석하면 CI(UTC)에서 9시간 어긋난다
                MapResponse response = call();

                //then - KST 16:08 = UTC 07:08
                assertThat(response.departureTime())
                        .isEqualTo(departedAt.atZone(SEOUL).toInstant());
            }
        }

        @Nested
        @DisplayName("Context: 대중교통 경로가 진행 중이면")
        class Context_with_transit_route {

            private MapResponse call() {
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS))
                        .willReturn(Optional.of(route(TransportMode.TRANSIT, transitSummary(0), transitReportJson())));
                return navigationService.getMapRoute(guardian);
            }

            @Test
            @DisplayName("It : mode를 TRANSIT으로 내보낸다")
            void it_returns_transit_mode() {
                //when
                MapResponse response = call();

                //then
                assertThat(response.mode()).isEqualTo(TransportMode.TRANSIT);
                assertThat(response.etaMin()).isEqualTo(15);
            }

            @Test
            @DisplayName("(도보는 steps · 대중교통은 path)It : leg마다 다른 자리에서 좌표를 모은다")
            void it_collects_path_from_both_places() {
                //when : 도보 leg은 passShape이 없어 path가 비고 좌표가 steps 안에 있다
                MapResponse response = call();

                //then : 도보 2개 + 버스 2개
                assertThat(response.path()).hasSize(4);
                assertThat(response.path().getFirst())
                        .containsEntry("latitude", 37.504585)
                        .containsEntry("longitude", 127.024798);
                assertThat(response.path().getLast())
                        .containsEntry("latitude", 37.479103)
                        .containsEntry("longitude", 127.037476);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getRoutesInWeek 메서드는")
    class Describe_with_getRoutesInWeek {

        private RouteSummary summary(Long id, RouteStatus status) {
            return new RouteSummary(id, "회사", LocalDateTime.now(SEOUL), status);
        }

        @Nested
        @DisplayName("Context: 피보호자가 호출하면")
        class Context_with_ward {

            @Test
            @DisplayName("It : 관계를 조회하지 않고 본인 id로 찾는다")
            void it_uses_own_id() {
                //given
                given(routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(wardId), any(LocalDateTime.class))).willReturn(List.of());

                //when
                navigationService.getRoutesInWeek(ward);

                //then
                verify(userService, never()).getWardIdFromGuardianId(anyLong());
            }
        }

        @Nested
        @DisplayName("Context: 보호자가 호출하면")
        class Context_with_guardian {

            @Test
            @DisplayName("It : 연결된 피보호자 id로 찾는다")
            void it_resolves_ward_id() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(wardId), any(LocalDateTime.class))).willReturn(List.of());

                //when
                navigationService.getRoutesInWeek(guardian);

                //then : 보호자 본인 id로 찾으면 남의 경로가 나오거나 빈 목록이 된다
                verify(routeRepository).findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(wardId), any(LocalDateTime.class));
            }
        }

        @Nested
        @DisplayName("Context: 조회 기준 시각은")
        class Context_with_time_window {

            @Test
            @DisplayName("It : 6일 전 KST 00:00이다")
            void it_starts_at_kst_midnight_six_days_ago() {
                //given
                given(routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(wardId), any(LocalDateTime.class))).willReturn(List.of());

                //when
                navigationService.getRoutesInWeek(ward);

                //then : createdAt은 DateTimeProvider가 KST 벽시계로 채운다. 여기가 어긋나면 하루가 통째로 빠진다
                ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
                verify(routeRepository)
                        .findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(eq(wardId), captor.capture());

                LocalDateTime expected = LocalDate.now(SEOUL).minusDays(6).atStartOfDay();
                assertThat(captor.getValue()).isEqualTo(expected);
                assertThat(captor.getValue().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
            }
        }

        @Nested
        @DisplayName("Context: 상태가 다른 경로가 섞여 있으면")
        class Context_with_mixed_status {

            @Test
            @DisplayName("It : 거르지 않고 조회 순서 그대로 반환한다")
            void it_returns_all_statuses() {
                //given : 취소·진행 중도 화면에 보여주기로 했다. 서버는 필터하지 않는다
                given(routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(wardId), any(LocalDateTime.class)))
                        .willReturn(List.of(
                                summary(12L, RouteStatus.IN_PROGRESS),
                                summary(11L, RouteStatus.COMPLETED),
                                summary(10L, RouteStatus.CANCELED)));

                //when
                List<RouteSummary> response = navigationService.getRoutesInWeek(ward);

                //then
                assertThat(response).extracting(RouteSummary::id).containsExactly(12L, 11L, 10L);
                assertThat(response).extracting(RouteSummary::status)
                        .containsExactly(RouteStatus.IN_PROGRESS, RouteStatus.COMPLETED, RouteStatus.CANCELED);
            }
        }

        @Nested
        @DisplayName("Context: 기간 안에 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 빈 목록을 반환한다")
            void it_returns_empty_list() {
                //given
                given(routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(wardId), any(LocalDateTime.class))).willReturn(List.of());

                //when&then
                assertThat(navigationService.getRoutesInWeek(ward)).isEmpty();
            }
        }
    }
}
