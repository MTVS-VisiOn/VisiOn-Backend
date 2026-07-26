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
        return new LocationRequest(latitude, longitude, 12.5f, 1.4f, Instant.now());
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
            @DisplayName("(accuracy와 speed가 없어도)It : 201 상태를 반환한다")
            void it_return_201_created_without_nullable_fields() throws Exception {
                //given
                LocationRequest request =
                        new LocationRequest(37.501274, 127.039585, null, null, Instant.now());

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
                        new LocationRequest(37.501274, 127.039585, 12.5f, 1.4f, null);

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
        @DisplayName("Context: 피보호자가 연결 상태이면")
        class Context_with_connected_ward {

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
        @DisplayName("Context: 피보호자가 연결 상태가 아니면")
        class Context_with_disconnected_ward {

            @Test
            @DisplayName("It : 200 상태와 주소 없는 응답을 반환한다")
            void it_return_200_ok_and_empty_address() throws Exception {
                //given
                LastLocationResponse response =
                        new LastLocationResponse(false, null, MovementStatus.UNKNOWN.getMessage());
                given(locationService.getLastLocation(any(CurrentUser.class))).willReturn(response);

                //when-then
                mockMvc.perform(
                                get("/api/locations")
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOCATION_READ.name()))
                        .andExpect(jsonPath("$.data.lastAddress").doesNotExist())
                        .andExpect(jsonPath("$.data.status").value(MovementStatus.UNKNOWN.getMessage()))
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
}
