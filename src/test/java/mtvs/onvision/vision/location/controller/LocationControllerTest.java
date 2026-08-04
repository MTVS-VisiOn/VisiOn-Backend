package mtvs.onvision.vision.location.controller;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.LastLocationResponse;
import mtvs.onvision.vision.location.dto.LocationRequest;
import mtvs.onvision.vision.location.dto.LocationSearchInfo;
import mtvs.onvision.vision.location.dto.LocationSearchResponse;
import mtvs.onvision.vision.location.service.LocationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@AutoConfigureMockMvc(addFilters = false)
class LocationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private LocationService locationService;

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

    private LocationRequest request(Double latitude, Double longitude) {
        return new LocationRequest(latitude, longitude, 12.5f, Instant.now());
    }

    @Nested
    @DisplayName("Describe: POST /api/locations 엔드포인트는")
    class receiveLocation {

        @Nested
        @DisplayName("Context: 올바른 좌표가 주어지면")
        class Context_with_available_location {

            @Test
            @DisplayName("It : 201 상태와 성공 메시지를 반환한다")
            void it_return_201_created_and_success_message() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request(37.501274, 127.039585)))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOCATION_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.LOCATION_CREATED.getSuccessMessage()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(accuracy가 없어도)It : 201 상태를 반환한다")
            void it_return_201_created_without_nullable_fields() throws Exception {
                //given
                LocationRequest request =
                        new LocationRequest(37.501274, 127.039585, null, Instant.now());

                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOCATION_CREATED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 올바르지 않은 request가 주어지면")
        class Context_with_request_error {

            @Test
            @DisplayName("(위도가 없을때)It : 400 상태와 검증 실패를 반환한다")
            void it_return_400_badRequest_and_latitude_not_null() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request(null, 127.039585)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(경도가 없을때)It : 400 상태와 검증 실패를 반환한다")
            void it_return_400_badRequest_and_longitude_not_null() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request(37.501274, null)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(위도가 범위를 벗어날때)It : 400 상태와 검증 실패를 반환한다")
            void it_return_400_badRequest_and_latitude_out_of_range() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request(100.0, 127.039585)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(경도가 범위를 벗어날때)It : 400 상태와 검증 실패를 반환한다")
            void it_return_400_badRequest_and_longitude_out_of_range() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request(37.501274, 200.0)))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(측정 시각이 없을때)It : 400 상태와 검증 실패를 반환한다")
            void it_return_400_badRequest_and_recorded_at_not_null() throws Exception {
                //given
                LocationRequest request =
                        new LocationRequest(37.501274, 127.039585, 12.5f, null);

                //when-then
                mockMvc.perform(
                                post("/api/locations")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/locations 엔드포인트는")
    class getLastLocation {

        @Nested
        @DisplayName("Context: 최근 위치가 존재하면")
        class Context_with_available_last_location {

            @Test
            @DisplayName("It : 200 상태와 주소, 이동 상태를 반환한다")
            void it_return_200_ok_and_last_location() throws Exception {
                //given
                LastLocationResponse response = new LastLocationResponse(
                        true, "경기도 부천시 원미구 부일로 123", MovementStatus.ON_FOOT.getMessage());
                given(locationService.getLastLocation(any(CurrentUser.class))).willReturn(response);

                //when-then
                mockMvc.perform(
                                get("/api/locations")
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOCATION_READ.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.LOCATION_READ.getSuccessMessage()))
                        .andExpect(jsonPath("$.data.lastAddress").value("경기도 부천시 원미구 부일로 123"))
                        .andExpect(jsonPath("$.data.status").value(MovementStatus.ON_FOOT.getMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 저장된 최근 위치가 없으면")
        class Context_with_no_last_location {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_LAST_LOCATION을 반환한다")
            void it_return_404_not_found_and_no_last_location() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_LAST_LOCATION))
                        .when(locationService)
                        .getLastLocation(any(CurrentUser.class));

                //when-then
                mockMvc.perform(
                                get("/api/locations")
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_LAST_LOCATION.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND_LAST_LOCATION.getMessage()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/locations/search 엔드포인트는")
    class searchLocation {

        private LocationSearchInfo info() {
            return new LocationSearchInfo(
                    "2874793", "287479301", "화목순대국 광화문1호점",
                    37.57120358, 126.97471568,
                    "서울 종로구 당주동 40", "서울 종로구 새문안로5길 11");
        }

        @Nested
        @DisplayName("Context: 검색 결과가 존재하면")
        class Context_with_results {

            @Test
            @DisplayName("It : 200 상태와 장소 목록을 반환한다")
            void it_return_200_ok_and_places() throws Exception {
                //given
                LocationSearchResponse response =
                        new LocationSearchResponse(1, 1, 1, List.of(info()));
                given(locationService.searchLocation("화목순대국")).willReturn(response);

                //when-then
                mockMvc.perform(
                                get("/api/locations/search")
                                        .param("keyword", "화목순대국")
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOCATION_SEARCH_READ.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.LOCATION_SEARCH_READ.getSuccessMessage()))
                        .andExpect(jsonPath("$.data.totalCount").value(1))
                        .andExpect(jsonPath("$.data.infos[0].name").value("화목순대국 광화문1호점"))
                        .andExpect(jsonPath("$.data.infos[0].landAddress").value("서울 종로구 당주동 40"))
                        .andExpect(jsonPath("$.data.infos[0].roadAddress").value("서울 종로구 새문안로5길 11"))
                        .andExpect(jsonPath("$.data.infos[0].noorLat").value(37.57120358))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 검색 결과가 없으면")
        class Context_with_no_result {

            @Test
            @DisplayName("It : 200 상태와 빈 목록을 반환한다")
            void it_return_200_ok_and_empty_list() throws Exception {
                //given : 결과 없음은 오류가 아니다
                given(locationService.searchLocation("asdfqwerzxcv"))
                        .willReturn(new LocationSearchResponse(0, 0, 0, List.of()));

                //when-then
                mockMvc.perform(
                                get("/api/locations/search")
                                        .param("keyword", "asdfqwerzxcv")
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOCATION_SEARCH_READ.name()))
                        .andExpect(jsonPath("$.data.totalCount").value(0))
                        .andExpect(jsonPath("$.data.infos").isEmpty())
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: keyword 파라미터가 없으면")
        class Context_with_no_keyword {

            @Test
            @DisplayName("It : 400 상태와 REQUESTPARAM_REQUIRED를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then
                mockMvc.perform(
                                get("/api/locations/search")
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.REQUESTPARAM_REQUIRED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: keyword가 빈 값이면")
        class Context_with_blank_keyword {

            @Test
            @DisplayName("(빈 문자열)It : 400 상태와 필수값 메시지를 반환한다")
            void it_return_400_bad_request_with_empty_string() throws Exception {
                //when-then : 파라미터 자체는 존재하므로 REQUESTPARAM_REQUIRED가 아니라 VALIDATION_FAILED
                mockMvc.perform(
                                get("/api/locations/search")
                                        .param("keyword", "")
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("keyword는 필수값입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(공백만)It : 400 상태와 필수값 메시지를 반환한다")
            void it_return_400_bad_request_with_whitespace() throws Exception {
                //when-then
                mockMvc.perform(
                                get("/api/locations/search")
                                        .param("keyword", "   ")
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("keyword는 필수값입니다."))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: keyword가 100자를 넘으면")
        class Context_with_too_long_keyword {

            @Test
            @DisplayName("It : 400 상태와 길이 제한 메시지를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //given
                String keyword = "가".repeat(101);

                //when-then
                mockMvc.perform(
                                get("/api/locations/search")
                                        .param("keyword", keyword)
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("keyword는 100자 이하여야 합니다."))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 티맵 호출이 실패하면")
        class Context_with_tmap_error {

            @Test
            @DisplayName("It : 502 상태와 TMAP_API_ERROR를 반환한다")
            void it_return_502_bad_gateway() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.TMAP_API_ERROR))
                        .when(locationService)
                        .searchLocation("화목순대국");

                //when-then
                mockMvc.perform(
                                get("/api/locations/search")
                                        .param("keyword", "화목순대국")
                        )
                        .andExpect(status().isBadGateway())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.TMAP_API_ERROR.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.TMAP_API_ERROR.getMessage()))
                        .andDo(print());
            }
        }
    }
}
