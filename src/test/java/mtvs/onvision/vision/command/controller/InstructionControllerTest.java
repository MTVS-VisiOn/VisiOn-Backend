package mtvs.onvision.vision.command.controller;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.dto.InstructionResponse;
import mtvs.onvision.vision.command.service.InstructionService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstructionController.class)
@AutoConfigureMockMvc(addFilters = false)
class InstructionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private InstructionService instructionService;

    @MockitoBean
    JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockitoBean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long guardianId = 1L;
    Long instructionId = 10L;
    CurrentUser currentUser = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);

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
    @DisplayName("Describe: POST /api/instructions 엔드포인트는")
    class saveInstruction {

        @Nested
        @DisplayName("Context: 문구를 보내면")
        class Context_with_valid_request {

            @Test
            @DisplayName("It : 201 상태와 성공 메시지를 반환한다")
            void it_return_201_created() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/instructions")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("잠시 멈추세요.")))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.code").value(SuccessCode.INSTRUCTION_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.INSTRUCTION_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 문구가 비어 있으면")
        class Context_with_blank_content {

            @Test
            @DisplayName("It : 400 상태와 VALIDATION_FAILED를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/instructions")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("   ")))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/instructions 엔드포인트는")
    class getInstructions {

        @Nested
        @DisplayName("Context: 등록한 문구가 있으면")
        class Context_with_instructions {

            @Test
            @DisplayName("It : 200 상태와 문구 목록을 반환한다")
            void it_return_200_with_content() throws Exception {
                //given
                given(instructionService.getInstructions(any()))
                        .willReturn(List.of(
                                new InstructionResponse(1L, "잠시 멈추세요."),
                                new InstructionResponse(2L, "횡단보도 입니다.")));

                //when-then
                mockMvc.perform(get("/api/instructions"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.INSTRUCTION_READ.name()))
                        .andExpect(jsonPath("$.data[0].id").value(1))
                        .andExpect(jsonPath("$.data[0].content").value("잠시 멈추세요."))
                        .andExpect(jsonPath("$.data[1].content").value("횡단보도 입니다."))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 등록한 문구가 없으면")
        class Context_without_instructions {

            @Test
            @DisplayName("It : 200 상태와 빈 배열을 반환한다")
            void it_return_200_with_empty_array() throws Exception {
                //given
                given(instructionService.getInstructions(any())).willReturn(List.of());

                //when-then
                mockMvc.perform(get("/api/instructions"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data").isEmpty())
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: PUT /api/instructions/{id} 엔드포인트는")
    class updateInstruction {

        @Nested
        @DisplayName("Context: 본인이 등록한 문구면")
        class Context_with_own_instruction {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then
                mockMvc.perform(
                                put("/api/instructions/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("천천히 가세요.")))
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.INSTRUCTION_UPDATED.name()))
                        .andDo(print());
            }

            @Test
            @DisplayName("It : 경로의 id를 그대로 서비스에 넘긴다")
            void it_passes_path_id() throws Exception {
                //when
                mockMvc.perform(
                                put("/api/instructions/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("천천히 가세요.")))
                        )
                        .andExpect(status().isOk());

                //then
                verify(instructionService).updateInstruction(eq(instructionId), any(), any());
            }
        }

        @Nested
        @DisplayName("Context: 없는 id면")
        class Context_with_unknown_id {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_INSTRUCTION을 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_INSTRUCTION))
                        .when(instructionService).updateInstruction(eq(instructionId), any(), any());

                //when-then
                mockMvc.perform(
                                put("/api/instructions/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("천천히 가세요.")))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_INSTRUCTION.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 다른 보호자가 등록한 문구면")
        class Context_with_others_instruction {

            @Test
            @DisplayName("It : 403 상태와 NOT_OWNER를 반환한다")
            void it_return_403_forbidden() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_OWNER))
                        .when(instructionService).updateInstruction(eq(instructionId), any(), any());

                //when-then
                mockMvc.perform(
                                put("/api/instructions/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new InstructionRequest("천천히 가세요.")))
                        )
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_OWNER.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: DELETE /api/instructions/{id} 엔드포인트는")
    class deleteInstruction {

        @Nested
        @DisplayName("Context: 본인이 등록한 문구면")
        class Context_with_own_instruction {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then
                mockMvc.perform(delete("/api/instructions/10").with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.INSTRUCTION_DELETED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 없는 id면")
        class Context_with_unknown_id {

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_INSTRUCTION을 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_INSTRUCTION))
                        .when(instructionService).deleteInstruction(eq(instructionId), any());

                //when-then
                mockMvc.perform(delete("/api/instructions/10").with(csrf()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_INSTRUCTION.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 다른 보호자가 등록한 문구면")
        class Context_with_others_instruction {

            @Test
            @DisplayName("It : 403 상태와 NOT_OWNER를 반환한다")
            void it_return_403_forbidden() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_OWNER))
                        .when(instructionService).deleteInstruction(eq(instructionId), any());

                //when-then
                mockMvc.perform(delete("/api/instructions/10").with(csrf()))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_OWNER.name()))
                        .andDo(print());
            }
        }
    }
}
