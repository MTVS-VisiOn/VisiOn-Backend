package mtvs.onvision.vision.presence.controller;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.presence.domain.NetworkType;
import mtvs.onvision.vision.presence.domain.PresenceType;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import mtvs.onvision.vision.presence.service.PresenceService;
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

@WebMvcTest(PresenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PresenceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private PresenceService presenceService;

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

    private HeartbeatRequest heartbeat(Boolean deviceConnected, Integer battery) {
        return new HeartbeatRequest(
                deviceConnected,
                battery,
                new HeartbeatRequest.NetworkRequest(NetworkType.LTE, true),
                Instant.now(),
                Instant.now()
        );
    }

    @Nested
    @DisplayName("Describe: POST /api/presence 엔드포인트는")
    class receiveHeartBeat {

        @Nested
        @DisplayName("Context: 올바른 heartbeat가 주어지면")
        class Context_with_available_heartbeat {

            @Test
            @DisplayName("It : 201 상태와 성공 메시지를 반환한다")
            void it_return_201_created_and_success_message() throws Exception {
                //given
                HeartbeatRequest request = heartbeat(true, 80);

                //when-then
                mockMvc.perform(
                                post("/api/presence")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.HEARTBEAT_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.HEARTBEAT_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 올바르지 않은 request가 주어지면")
        class Context_with_request_error {

            @Test
            @DisplayName("(연결상태가 없을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_device_connected_not_null() throws Exception {
                //given
                HeartbeatRequest request = heartbeat(null, 80);

                //when-then
                mockMvc.perform(
                                post("/api/presence")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("연결상태는 필수값입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(배터리가 없을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_battery_not_null() throws Exception {
                //given
                HeartbeatRequest request = heartbeat(true, null);

                //when-then
                mockMvc.perform(
                                post("/api/presence")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("배터리 상태는 필수값입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(배터리가 100을 초과할때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_battery_max() throws Exception {
                //given
                HeartbeatRequest request = heartbeat(true, 101);

                //when-then
                mockMvc.perform(
                                post("/api/presence")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("배터리는 100 이하입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(배터리가 0 미만일때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_battery_min() throws Exception {
                //given
                HeartbeatRequest request = heartbeat(true, -1);

                //when-then
                mockMvc.perform(
                                post("/api/presence")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("배터리는 0 이상입니다."))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/presence 엔드포인트는")
    class getWardPresence {

        @Nested
        @DisplayName("Context: 인증된 보호자가 요청하면")
        class Context_with_authenticated_guardian {

            @Test
            @DisplayName("It : 200 상태와 피보호자의 기기 상태를 반환한다")
            void it_return_200_ok_and_presence() throws Exception {
                //given
                PresenceResponse response =
                        new PresenceResponse(77, true, PresenceType.NORMAL.getDescription());
                given(presenceService.getWardPresence(any(CurrentUser.class))).willReturn(response);

                //when-then
                mockMvc.perform(
                                get("/api/presence")
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.PRESENCE_READ.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.PRESENCE_READ.getSuccessMessage()))
                        .andExpect(jsonPath("$.data.battery").value(77))
                        .andExpect(jsonPath("$.data.deviceConnected").value(true))
                        .andExpect(jsonPath("$.data.status").value(PresenceType.NORMAL.getDescription()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 로그인한 보호자의 관계가 존재하지 않으면")
        class Context_with_relation_not_found {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_GUARDIAN을 반환한다")
            void it_return_404_not_found_and_no_guardian() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_GUARDIAN))
                        .when(presenceService)
                        .getWardPresence(any(CurrentUser.class));

                //when-then
                mockMvc.perform(
                                get("/api/presence")
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_GUARDIAN.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND_GUARDIAN.getMessage()))
                        .andDo(print());
            }
        }
    }
}
