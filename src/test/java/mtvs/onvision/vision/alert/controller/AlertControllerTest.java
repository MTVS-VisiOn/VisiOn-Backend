package mtvs.onvision.vision.alert.controller;

import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.dto.AlertResponse;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.alert.service.AlertService;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private AlertService alertService;

    @MockitoBean
    JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockitoBean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long wardId = 2L;
    Long guardianId = 1L;
    Long alertId = 10L;
    Instant occurredAt = Instant.parse("2026-08-05T09:12:33.512Z");

    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);
    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);

    private void authenticate(CurrentUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ObstacleRequest obstacle(Instant occurredAt, Double latitude, String message) {
        return new ObstacleRequest(occurredAt, latitude, 127.0276, message, "위험 음성 재생");
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile("image", "obstacle.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy".getBytes());
    }

    private MockMultipartFile requestPart(ObstacleRequest request) {
        return new MockMultipartFile("request", "request",
                MediaType.APPLICATION_JSON_VALUE, om.writeValueAsBytes(request));
    }

    @Nested
    @DisplayName("Describe: POST /api/alerts/detect/obstacle 엔드포인트는")
    class detectObstacle {

        @BeforeEach
        void setUpAuthentication() {
            authenticate(ward);
        }

        @Nested
        @DisplayName("Context: 올바른 감지 정보와 이미지가 주어지면")
        class Context_with_available_data {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok_and_success_message() throws Exception {
                //given
                ObstacleRequest request = obstacle(occurredAt, 37.4979, "전방 2m에 자전거가 세워져 있습니다");

                //when-then
                mockMvc.perform(
                                multipart("/api/alerts/detect/obstacle")
                                        .file(requestPart(request))
                                        .file(imagePart())
                                        .with(csrf())
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.DETECT_OBSTACLE_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.DETECT_OBSTACLE_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 올바르지 않은 request가 주어지면")
        class Context_with_request_error {

            @Test
            @DisplayName("(감지 시각이 없을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_when_occurred_at_is_null() throws Exception {
                //given
                ObstacleRequest request = obstacle(null, 37.4979, "전방 2m에 자전거가 세워져 있습니다");

                //when-then
                mockMvc.perform(
                                multipart("/api/alerts/detect/obstacle")
                                        .file(requestPart(request))
                                        .file(imagePart())
                                        .with(csrf())
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("감지 시각은 필수값입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(위도가 없을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_when_latitude_is_null() throws Exception {
                //given
                ObstacleRequest request = obstacle(occurredAt, null, "전방 2m에 자전거가 세워져 있습니다");

                //when-then
                mockMvc.perform(
                                multipart("/api/alerts/detect/obstacle")
                                        .file(requestPart(request))
                                        .file(imagePart())
                                        .with(csrf())
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("위도는 필수값입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(감지 내용이 없을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_when_message_is_null() throws Exception {
                //given
                ObstacleRequest request = obstacle(occurredAt, 37.4979, null);

                //when-then
                mockMvc.perform(
                                multipart("/api/alerts/detect/obstacle")
                                        .file(requestPart(request))
                                        .file(imagePart())
                                        .with(csrf())
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("감지 내용은 필수값입니다."))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/alerts/{alertId} 엔드포인트는")
    class getAlertDetail {

        @BeforeEach
        void setUpAuthentication() {
            authenticate(guardian);
        }

        AlertResponse response = new AlertResponse(
                AlertType.OBSTACLE,
                occurredAt,
                "서울특별시 강남구 테헤란로 152",
                "https://onvision.s3.ap-northeast-2.amazonaws.com/alerts/obstacle.jpg?X-Amz-Signature=abc",
                "전방 2m에 자전거가 세워져 있습니다",
                "위험 음성 재생"
        );

        @Nested
        @DisplayName("Context: 자기 피보호자의 알림 id가 주어지면")
        class Context_with_own_ward_alert {

            @Test
            @DisplayName("It : 200 상태와 상세 정보를 반환한다")
            void it_return_200_ok_and_detail() throws Exception {
                //given
                given(alertService.getAlertDetail(anyLong(), any(CurrentUser.class))).willReturn(response);

                //when-then
                mockMvc.perform(get("/api/alerts/{alertId}", alertId))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.ALERT_READ.name()))
                        .andExpect(jsonPath("$.data.type").value(AlertType.OBSTACLE.name()))
                        .andExpect(jsonPath("$.data.occurredPlace").value(response.occurredPlace()))
                        .andExpect(jsonPath("$.data.presignedUrl").value(response.presignedUrl()))
                        .andExpect(jsonPath("$.data.content").value(response.content()))
                        .andExpect(jsonPath("$.data.action").value(response.action()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 없는 알림 id가 주어지면")
        class Context_with_unknown_alert {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_ALERT를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_ALERT))
                        .when(alertService).getAlertDetail(anyLong(), any(CurrentUser.class));

                //when-then
                mockMvc.perform(get("/api/alerts/{alertId}", alertId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ALERT.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 다른 피보호자의 알림 id가 주어지면")
        class Context_with_others_alert {

            @Test
            @DisplayName("It : 403 상태와 NOT_GUARDIAN을 반환한다")
            void it_return_403_forbidden() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_GUARDIAN))
                        .when(alertService).getAlertDetail(anyLong(), any(CurrentUser.class));

                //when-then
                mockMvc.perform(get("/api/alerts/{alertId}", alertId))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_GUARDIAN.name()))
                        .andDo(print());
            }
        }
    }
}
