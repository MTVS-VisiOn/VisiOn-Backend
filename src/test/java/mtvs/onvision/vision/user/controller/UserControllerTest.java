package mtvs.onvision.vision.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.service.UserService;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockitoBean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long wardId = 2L;
    String email = "user@test.com";
    String password = "password1234";
    String nickname = "테스트유저";
    String phoneNumber = "010-1234-5678";

    @Nested
    @DisplayName("Describe: POST /signup 엔드포인트는")
    class signup {
        SignupRequest request;

        @Nested
        @DisplayName("Context: role이 WARD이고 올바른 데이터가 주어지면")
        class Context_with_ward_role {
            @BeforeEach
            void setUp() {
                request = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok_and_success_message() throws Exception {
                //given
                doNothing().when(userService).signup(request);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.USER_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.USER_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 올바른 wardId가 주어지면")
        class Context_with_guardian_role {
            @BeforeEach
            void setUp() {
                request = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, wardId);
            }

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok_and_success_message() throws Exception {
                //given
                doNothing().when(userService).signup(request);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.USER_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.USER_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 만료되었거나 유효하지 않은 토큰이 주어지면")
        class Context_with_expired_token {
            @BeforeEach
            void setUp() {
                request = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : /signup은 anonymous 경로라 토큰 상태와 무관하게 200 상태를 반환한다")
            void it_return_200_ok_regardless_of_token() throws Exception {
                //given
                doNothing().when(userService).signup(request);

                //when-then
                // SecurityConfig상 /api/users/signup은 .anonymous() 경로이고,
                // 이 테스트는 addFilters=false라 JwtAuthenticationFilter 자체가 동작하지 않는다.
                // 즉 Authorization 헤더에 만료된 토큰을 넣어도 검증되지 않고 그대로 통과한다.
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .header("Authorization", "Bearer expired.jwt.token")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.USER_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.USER_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 wardId가 없으면")
        class Context_with_guardian_role_and_no_ward_id {
            @BeforeEach
            void setUp() {
                request = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, null);
            }

            @Test
            @DisplayName("It : 400 상태와 INVALID_WARD를 반환한다")
            void it_return_400_badRequest_and_invalid_ward() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_WARD.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_WARD.getMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 올바르지 않은 request가 주어지면")
        class Context_with_request_error {
            @BeforeEach
            void setUp() {
            }

            @Test
            @DisplayName("(이메일이 비었을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_email_not_blank() throws Exception {
                //given
                request = new SignupRequest("", password, nickname, phoneNumber, UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("이메일은 필수입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(이메일 형식이 올바르지 않을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_email_invalid_format() throws Exception {
                //given
                request = new SignupRequest("invalid-email", password, nickname, phoneNumber, UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("올바른 이메일 형식이 아닙니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(비밀번호가 비었을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_password_not_blank() throws Exception {
                //given
                request = new SignupRequest(email, "", nickname, phoneNumber, UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("비밀번호는 필수입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(비밀번호가 8자 미만일때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_password_min_size() throws Exception {
                //given
                request = new SignupRequest(email, "1234567", nickname, phoneNumber, UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("비밀번호는 최소 8자 이상이어야 합니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(이름이 비었을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_nickname_not_blank() throws Exception {
                //given
                request = new SignupRequest(email, password, "", phoneNumber, UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("이름은 필수입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(전화번호가 비었을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_phoneNumber_not_blank() throws Exception {
                //given
                request = new SignupRequest(email, password, nickname, "", UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("전화번호는 필수입니다."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(전화번호 형식이 올바르지 않을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_phoneNumber_invalid_format() throws Exception {
                //given
                request = new SignupRequest(email, password, nickname, "010123456", UserRole.WARD, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("전화번호 형식은 010-0000-0000로 작성해주세요."))
                        .andDo(print());
            }

            @Test
            @DisplayName("(역할이 없을때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_role_not_null() throws Exception {
                //given
                request = new SignupRequest(email, password, nickname, phoneNumber, null, null);

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andExpect(jsonPath("$.message").value("유저의 역할은 필수입니다."))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 비즈니스 예외가 발생하면")
        class Context_with_business_error {
            @BeforeEach
            void setUp() {
                request = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("(이메일이 중복일때)It : 409 상태와 이메일 중복을 반환한다")
            void it_return_409_conflict_and_exist_email() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.EXIST_EMAIL))
                        .when(userService)
                        .signup(eq(request));

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isConflict())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.EXIST_EMAIL.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.EXIST_EMAIL.getMessage()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(전화번호가 중복일때)It : 409 상태와 전화번호 중복을 반환한다")
            void it_return_409_conflict_and_exist_phone_number() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.EXIST_PHONENUMBER))
                        .when(userService)
                        .signup(eq(request));

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isConflict())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.EXIST_PHONENUMBER.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.EXIST_PHONENUMBER.getMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 wardId에 해당하는 피보호자가 없으면")
        class Context_with_ward_not_found {
            @BeforeEach
            void setUp() {
                request = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, wardId);
            }

            @Test
            @DisplayName("It : 404 상태와 NOT_FOUND_WARD를 반환한다")
            void it_return_404_not_found_and_no_ward() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_WARD))
                        .when(userService)
                        .signup(eq(request));

                //when-then
                mockMvc.perform(
                                post("/api/users/signup")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_WARD.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND_WARD.getMessage()))
                        .andDo(print());
            }
        }
    }
}
