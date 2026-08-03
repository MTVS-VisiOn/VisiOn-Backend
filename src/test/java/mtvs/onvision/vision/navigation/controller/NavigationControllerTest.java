package mtvs.onvision.vision.navigation.controller;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.LocationInfo;
import mtvs.onvision.vision.navigation.dto.MapResponse;
import mtvs.onvision.vision.navigation.dto.NavigationPreRequest;
import mtvs.onvision.vision.navigation.dto.NavigationResponse;
import mtvs.onvision.vision.navigation.dto.RouteRequest;
import mtvs.onvision.vision.navigation.service.NavigationService;
import mtvs.onvision.vision.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NavigationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NavigationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private NavigationService navigationService;

    @MockitoBean
    JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockitoBean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long wardId = 2L;
    CurrentUser currentUser = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LocationInfo location(String name) {
        return new LocationInfo(name, name, 37.504585, 127.024798, "서울 강남구 강남대로 지하 476", null);
    }

    private NavigationPreRequest preRequest(TransportMode mode) {
        return new NavigationPreRequest(mode, location("신논현역"), location("말죽거리공원사거리"));
    }

    /** report는 DB에 있는 JSON 문자열이다. @JsonRawValue가 이걸 본문에 그대로 박는다 */
    private NavigationResponse response() {
        return new NavigationResponse(
                7L, TransportMode.TRANSIT,
                location("신논현역"), location("말죽거리공원사거리"),
                "{\"summary\":{\"mode\":\"TRANSIT\",\"totalTime\":953},\"legs\":[]}");
    }

    @Nested
    @DisplayName("Describe: POST /api/navigations/search 엔드포인트는")
    class searchNavigation {

        @Nested
        @DisplayName("Context: mode가 TRANSIT이면")
        class Context_with_transit_mode {

            @Test
            @DisplayName("It : 400 상태와 INVALID_TRANSFER를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then : 대중교통은 /search/transit로 가야 한다
                mockMvc.perform(
                                post("/api/navigations/search")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(preRequest(TransportMode.TRANSIT)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TRANSFER.name()))
                        .andDo(print());
            }

            @Test
            @DisplayName("It : 서비스를 호출하지 않는다")
            void it_does_not_call_service() throws Exception {
                //when
                mockMvc.perform(
                        post("/api/navigations/search")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(preRequest(TransportMode.TRANSIT))));

                //then : 잘못된 mode는 입력 검증에서 탈락시킨다
                verify(navigationService, never()).searchNavigation(any(), any());
            }
        }

        @Nested
        @DisplayName("Context: mode가 비어 있으면")
        class Context_without_mode {

            @Test
            @DisplayName("It : 400 상태와 VALIDATION_FAILED를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/navigations/search")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(preRequest(null)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: POST /api/navigations/search/transit 엔드포인트는")
    class searchNavigationTransit {

        @Nested
        @DisplayName("Context: mode가 TRANSIT이 아니면")
        class Context_with_non_transit_mode {

            @Test
            @DisplayName("It : 400 상태와 INVALID_TRANSFER를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/navigations/search/transit")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(preRequest(TransportMode.WALK)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TRANSFER.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: POST /api/navigations 엔드포인트는")
    class saveRoute {

        @Nested
        @DisplayName("Context: 보행자 경로를 고르면")
        class Context_with_walk {

            @Test
            @DisplayName("(index 없이도)It : 201 상태와 성공 메시지를 반환한다")
            void it_return_201_created() throws Exception {
                //when-then : index는 대중교통에만 필요하다
                mockMvc.perform(
                                post("/api/navigations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new RouteRequest(TransportMode.WALK, null)))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.ROUTE_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 대중교통인데 index가 없으면")
        class Context_with_transit_without_index {

            @Test
            @DisplayName("It : 400 상태와 INVALID_TRANSIT_INDEX를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then : 후보가 여러 개라 어느 걸 골랐는지 없으면 저장할 수 없다
                mockMvc.perform(
                                post("/api/navigations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new RouteRequest(TransportMode.TRANSIT, null)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TRANSIT_INDEX.name()))
                        .andDo(print());

                verify(navigationService, never()).saveRoute(any(), any());
            }
        }

        @Nested
        @DisplayName("Context: 대중교통이고 index가 있으면")
        class Context_with_transit_and_index {

            @Test
            @DisplayName("(index 0도)It : 201 상태를 반환한다")
            void it_return_201_created() throws Exception {
                //when-then : 0은 유효한 index다. null 검사여야지 falsy 검사면 안 된다
                mockMvc.perform(
                                post("/api/navigations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new RouteRequest(TransportMode.TRANSIT, 0)))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_CREATED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: Redis에 후보가 없으면")
        class Context_with_expired_candidates {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_ROUTE를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_ROUTE))
                        .when(navigationService).saveRoute(any(), any());

                //when-then
                mockMvc.perform(
                                post("/api/navigations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new RouteRequest(TransportMode.WALK, null)))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ROUTE.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/navigations/processing 엔드포인트는")
    class getProcessingRoute {

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : 200 상태와 경로를 반환한다")
            void it_return_200_with_content() throws Exception {
                //given
                given(navigationService.getProcessingRoute(any())).willReturn(response());

                //when-then
                mockMvc.perform(get("/api/navigations/processing"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_READ.name()))
                        .andExpect(jsonPath("$.data.id").value(7))
                        .andExpect(jsonPath("$.data.mode").value("TRANSIT"))
                        .andDo(print());
            }

            @Test
            @DisplayName("(@JsonRawValue)It : report를 이스케이프 문자열이 아니라 객체로 내보낸다")
            void it_writes_report_as_object() throws Exception {
                //given : 붙어 있지 않으면 report가 "{\"summary\"...}" 문자열로 나가 아래 경로가 안 잡힌다
                given(navigationService.getProcessingRoute(any())).willReturn(response());

                //when-then
                mockMvc.perform(get("/api/navigations/processing"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.report.summary.mode").value("TRANSIT"))
                        .andExpect(jsonPath("$.data.report.summary.totalTime").value(953))
                        .andExpect(jsonPath("$.data.report.legs").isArray())
                        .andDo(print());
            }

            @Test
            @DisplayName("(위도 37 · 경도 127)It : 좌표를 뒤집지 않고 내보낸다")
            void it_writes_coordinate_in_lat_lon_order() throws Exception {
                //given
                given(navigationService.getProcessingRoute(any())).willReturn(response());

                //when-then
                mockMvc.perform(get("/api/navigations/processing"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.start.latitude").value(37.504585))
                        .andExpect(jsonPath("$.data.start.longitude").value(127.024798))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_ROUTE를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                given(navigationService.getProcessingRoute(any()))
                        .willThrow(new BusinessException(ErrorCode.NOT_FOUND_ROUTE));

                //when-then
                mockMvc.perform(get("/api/navigations/processing"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ROUTE.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: PATCH /api/navigations/complete 엔드포인트는")
    class completeRoute {

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then
                mockMvc.perform(patch("/api/navigations/complete").with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_COMPLETED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_ROUTE를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_ROUTE))
                        .when(navigationService).completeRoute(any());

                //when-then
                mockMvc.perform(patch("/api/navigations/complete").with(csrf()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ROUTE.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: PATCH /api/navigations/cancel 엔드포인트는")
    class cancelRoute {

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then
                mockMvc.perform(patch("/api/navigations/cancel").with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_CANCELED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_ROUTE를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_ROUTE))
                        .when(navigationService).cancelRoute(any());

                //when-then
                mockMvc.perform(patch("/api/navigations/cancel").with(csrf()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ROUTE.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/navigations/map 엔드포인트는")
    class getMapRoute {

        private MapResponse mapResponse() {
            return new MapResponse(
                    "말죽거리공원사거리", "서울 서초구 강남대로 213", 37.479103, 127.037476,
                    24269, 360, Instant.parse("2026-08-03T07:08:00Z"), TransportMode.WALK,
                    List.of(Map.of("latitude", 37.504585, "longitude", 127.024798),
                            Map.of("latitude", 37.503900, "longitude", 127.025200)));
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 있으면")
        class Context_with_route {

            @Test
            @DisplayName("It : 200 상태와 목적지·소요시간을 반환한다")
            void it_return_200_with_content() throws Exception {
                //given
                given(navigationService.getMapRoute(any())).willReturn(mapResponse());

                //when-then
                mockMvc.perform(get("/api/navigations/map"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_READ.name()))
                        .andExpect(jsonPath("$.data.name").value("말죽거리공원사거리"))
                        .andExpect(jsonPath("$.data.distanceM").value(24269))
                        .andExpect(jsonPath("$.data.etaMin").value(360))
                        .andExpect(jsonPath("$.data.mode").value("WALK"))
                        .andDo(print());
            }

            @Test
            @DisplayName("(위도 37 · 경도 127)It : path를 좌표 객체 배열로 내보낸다")
            void it_writes_path_as_object_array() throws Exception {
                //given : 프론트는 [위도, 경도] 쌍이 아니라 {latitude, longitude}를 받는다
                given(navigationService.getMapRoute(any())).willReturn(mapResponse());

                //when-then
                mockMvc.perform(get("/api/navigations/map"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.path").isArray())
                        .andExpect(jsonPath("$.data.path[0].latitude").value(37.504585))
                        .andExpect(jsonPath("$.data.path[0].longitude").value(127.024798))
                        .andDo(print());
            }

            @Test
            @DisplayName("(ISO8601 UTC)It : departureTime을 Z가 붙은 문자열로 내보낸다")
            void it_writes_departure_time_as_utc() throws Exception {
                //given : 숫자(epoch)나 Z 없는 문자열로 나가면 프론트가 경과시간을 못 만든다
                given(navigationService.getMapRoute(any())).willReturn(mapResponse());

                //when-then
                mockMvc.perform(get("/api/navigations/map"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.departureTime").value("2026-08-03T07:08:00Z"))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 진행 중인 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 404가 아니라 200과 data:null을 반환한다")
            void it_return_200_with_null_data() throws Exception {
                //given : 목적지 미설정은 오류가 아니다
                given(navigationService.getMapRoute(any())).willReturn(null);

                //when-then
                mockMvc.perform(get("/api/navigations/map"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.code").value(SuccessCode.ROUTE_READ.name()))
                        .andExpect(jsonPath("$.data").doesNotExist())
                        .andDo(print());
            }
        }
    }
}
