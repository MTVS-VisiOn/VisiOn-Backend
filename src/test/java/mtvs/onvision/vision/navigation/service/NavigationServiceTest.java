package mtvs.onvision.vision.navigation.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
                assertThat(response.start().roadAddress()).isEqualTo("서울 강남구 강남대로 지하 476");
                assertThat(response.end().latitude()).isEqualTo(37.479103);
                assertThat(response.end().longitude()).isEqualTo(127.037476);
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
}
