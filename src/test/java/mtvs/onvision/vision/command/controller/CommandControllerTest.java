package mtvs.onvision.vision.command.controller;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.dto.CommandResponse;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.service.CommandService;
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

@WebMvcTest(CommandController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommandControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private CommandService commandService;

    @MockitoBean
    JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockitoBean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long guardianId = 1L;
    CurrentUser currentUser = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);

    Instant occurredAt = Instant.parse("2026-08-10T05:31:00Z");

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Describe: POST /api/commands/instruction 엔드포인트는")
    class guardianInstruct {

        @Nested
        @DisplayName("Context: 지시 내용을 보내면")
        class Context_with_valid_request {

            @Test
            @DisplayName("It : 201 상태와 성공 메시지를 반환한다")
            void it_return_201_created() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/commands/instruction")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("잠시 멈추세요.")))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.code").value(SuccessCode.COMMAND_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.COMMAND_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 지시 내용이 비어 있으면")
        class Context_with_blank_content {

            @Test
            @DisplayName("It : 400 상태와 VALIDATION_FAILED를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then : 빈 문자열을 TTS로 읽을 수 없으므로 @NotBlank가 걸러야 한다
                mockMvc.perform(
                                post("/api/commands/instruction")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("   ")))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 연결된 피보호자가 없으면")
        class Context_without_relation {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_RELATION을 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_RELATION))
                        .when(commandService).guardianInstruct(any(), any());

                //when-then
                mockMvc.perform(
                                post("/api/commands/instruction")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("잠시 멈추세요.")))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_RELATION.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/commands 엔드포인트는")
    class getInstructs {

        @Nested
        @DisplayName("Context: 보낸 지시가 있으면")
        class Context_with_commands {

            @Test
            @DisplayName("It : 200 상태와 최근 지시 목록을 반환한다")
            void it_return_200_with_content() throws Exception {
                //given
                given(commandService.getInstructs(any()))
                        .willReturn(List.of(
                                new CommandResponse(3L, "잠시 멈추세요.", occurredAt),
                                new CommandResponse(2L, "횡단보도 입니다.", occurredAt)));

                //when-then
                mockMvc.perform(get("/api/commands"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.COMMAND_READ.name()))
                        .andExpect(jsonPath("$.data[0].id").value(3))
                        .andExpect(jsonPath("$.data[0].content").value("잠시 멈추세요."))
                        .andExpect(jsonPath("$.data[1].id").value(2))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 보낸 지시가 없으면")
        class Context_without_commands {

            @Test
            @DisplayName("It : 200 상태와 빈 배열을 반환한다")
            void it_return_200_with_empty_array() throws Exception {
                //given
                given(commandService.getInstructs(any())).willReturn(List.of());

                //when-then
                mockMvc.perform(get("/api/commands"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.COMMAND_READ.name()))
                        .andExpect(jsonPath("$.data").isEmpty())
                        .andDo(print());
            }
        }
    }
}
