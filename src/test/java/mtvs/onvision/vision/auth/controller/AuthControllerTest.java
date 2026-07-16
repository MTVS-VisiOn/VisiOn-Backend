package mtvs.onvision.vision.auth.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.auth.dto.RefreshRequest;
import mtvs.onvision.vision.common.config.SecurityConfig;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// addFilters=false를 쓰지 않는다: logout에서 미인증 요청이 실제로 401(JwtAuthenticationEntryPoint)로
// 응답하는지 검증하려면 SecurityConfig의 실제 필터 체인(ExceptionTranslationFilter 포함)이 동작해야 한다.
// SecurityConfig는 순수 @Configuration이라 @WebMvcTest 슬라이스가 자동으로 스캔하지 않으므로 @Import로 직접 로드한다.
// JwtAuthenticationEntryPoint / JwtAccessDeniedHandler는 의존성이 없는 순수 컴포넌트라 마찬가지로 @Import로 실제 빈을 사용하고,
// JwtAuthenticationFilter만 UserService/JwtTokenProvider 의존성 때문에 mock으로 대체한 뒤 통과(pass-through)하도록 만든다.
@WebMvcTest(AuthController.class)
@Import({JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long userId = 1L;
    String email = "user@test.com";
    String password = "password1234";

    @BeforeEach
    void setUpJwtFilterPassThrough() throws Exception {
        // 토큰이 없거나 만료된 상황을 재현한다: 인증을 세팅하지 않고 다음 필터로 그대로 넘긴다.
        doAnswer(invocation -> {
            ServletRequest servletRequest = invocation.getArgument(0);
            ServletResponse servletResponse = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(servletRequest, servletResponse);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Nested
    @DisplayName("Describe: POST /login 엔드포인트는")
    class login {
        LoginRequest request;
        KeyPair keyPair = new KeyPair("accessToken", "refreshToken");

        @Nested
        @DisplayName("Context: 올바른 데이터가 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setUp() {
                request = new LoginRequest(email, password);
            }

            @Test
            @DisplayName("It : 200 상태와 성공 메시지, KeyPair를 반환한다")
            void it_return_200_ok_and_success_message_and_data() throws Exception {
                //given
                given(userService.login(request)).willReturn(keyPair);

                //when-then
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOGIN_SUCCESS.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.LOGIN_SUCCESS.getSuccessMessage()))
                        .andExpect(jsonPath("$.data.accessToken").value(keyPair.accessToken()))
                        .andExpect(jsonPath("$.data.refreshToken").value(keyPair.refreshToken()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 만료되었거나 유효하지 않은 토큰이 주어지면")
        class Context_with_expired_token {
            @BeforeEach
            void setUp() {
                request = new LoginRequest(email, password);
            }

            @Test
            @DisplayName("It : /login은 anonymous 경로라 토큰 상태와 무관하게 200 상태를 반환한다")
            void it_return_200_ok_regardless_of_token() throws Exception {
                //given
                given(userService.login(request)).willReturn(keyPair);

                //when-then
                // SecurityConfig상 /api/auth/login은 .anonymous() 경로이다.
                // JwtAuthenticationFilter는 pass-through로 대체되어 있어 만료된 토큰이 와도
                // 인증을 세팅하지 않고 그대로 넘기므로, anonymous 요청으로 취급되어 정상 처리된다.
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .header("Authorization", "Bearer expired.jwt.token")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOGIN_SUCCESS.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.LOGIN_SUCCESS.getSuccessMessage()))
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
                request = new LoginRequest("", password);

                //when-then
                mockMvc.perform(
                                post("/api/auth/login")
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
            @DisplayName("(비밀번호가 8자 미만일때)It : 400 상태와 검증 실패 이유를 반환한다")
            void it_return_400_badRequest_and_password_min_size() throws Exception {
                //given
                request = new LoginRequest(email, "1234567");

                //when-then
                mockMvc.perform(
                                post("/api/auth/login")
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
        }

        @Nested
        @DisplayName("Context: 비즈니스 예외가 발생하면")
        class Context_with_business_error {
            @BeforeEach
            void setUp() {
                request = new LoginRequest(email, password);
            }

            @Test
            @DisplayName("(존재하지 않는 이메일일때)It : 404 상태와 실패 메시지를 반환한다")
            void it_return_404_not_found_and_no_user() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_USER))
                        .when(userService)
                        .login(request);

                //when-then
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_USER.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND_USER.getMessage()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(비밀번호가 일치하지 않을때)It : 400 상태와 실패 메시지를 반환한다")
            void it_return_400_badRequest_and_not_match_password() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_MATCH_PASSWORD))
                        .when(userService)
                        .login(request);

                //when-then
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_MATCH_PASSWORD.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.NOT_MATCH_PASSWORD.getMessage()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: POST /refresh 엔드포인트는")
    class refresh {
        RefreshRequest request;
        KeyPair keyPair = new KeyPair("newAccessToken", "newRefreshToken");

        @Nested
        @DisplayName("Context: 올바른 refreshToken이 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setUp() {
                request = new RefreshRequest("validRefreshToken");
            }

            @Test
            @DisplayName("It : 200 상태와 성공 메시지, KeyPair를 반환한다")
            void it_return_200_ok_and_success_message_and_data() throws Exception {
                //given
                given(userService.refreshToken(request)).willReturn(keyPair);

                //when-then
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.REFRESH_SUCCESS.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.REFRESH_SUCCESS.getSuccessMessage()))
                        .andExpect(jsonPath("$.data.accessToken").value(keyPair.accessToken()))
                        .andExpect(jsonPath("$.data.refreshToken").value(keyPair.refreshToken()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 만료되었거나 유효하지 않은 액세스 토큰이 주어지면")
        class Context_with_expired_token {
            @BeforeEach
            void setUp() {
                request = new RefreshRequest("validRefreshToken");
            }

            @Test
            @DisplayName("It : /refresh는 permitAll 경로라 (액세스)토큰 상태와 무관하게 200 상태를 반환한다")
            void it_return_200_ok_regardless_of_token() throws Exception {
                //given
                given(userService.refreshToken(request)).willReturn(keyPair);

                //when-then
                // SecurityConfig상 /api/users/refresh는 .permitAll() 경로이다.
                // JwtAuthenticationFilter는 pass-through로 대체되어 있어
                // Authorization 헤더의 만료된 액세스 토큰은 검증되지 않고 그대로 통과한다.
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .with(csrf())
                                        .header("Authorization", "Bearer expired.jwt.token")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.REFRESH_SUCCESS.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.REFRESH_SUCCESS.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 비즈니스 예외가 발생하면")
        class Context_with_business_error {
            @BeforeEach
            void setUp() {
                request = new RefreshRequest("invalidRefreshToken");
            }

            @Test
            @DisplayName("(저장된 refreshToken이 없을때)It : 404 상태와 실패 메시지를 반환한다")
            void it_return_404_not_found_and_no_refresh() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_REFRESH))
                        .when(userService)
                        .refreshToken(request);

                //when-then
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_REFRESH.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND_REFRESH.getMessage()))
                        .andDo(print());
            }

            @Test
            @DisplayName("(refreshToken이 일치하지 않을때)It : 400 상태와 실패 메시지를 반환한다")
            void it_return_400_badRequest_and_invalid_refresh_token() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN))
                        .when(userService)
                        .refreshToken(request);

                //when-then
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REFRESH_TOKEN.name()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REFRESH_TOKEN.getMessage()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: DELETE /logout 엔드포인트는")
    class logout {

        CurrentUser currentUser = new CurrentUser(userId, email, UserRole.WARD);
        TestingAuthenticationToken authenticationToken = new TestingAuthenticationToken(currentUser, null, "ROLE_WARD");

        @Nested
        @DisplayName("Context: 인증된 유저가 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setUp() {
            }

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok_and_success_message() throws Exception {
                //given
                doNothing().when(userService).logout(currentUser);

                //when-then
                mockMvc.perform(
                                delete("/api/auth/logout")
                                        .with(csrf())
                                        .with(authentication(authenticationToken))
                        )
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.LOGOUT_SUCCESS.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.LOGOUT_SUCCESS.getSuccessMessage()))
                        .andDo(print());

                verify(userService).logout(currentUser);
            }
        }

        @Nested
        @DisplayName("Context: 인증 정보(토큰) 없이 요청하면")
        class Context_without_authentication {
            @Test
            @DisplayName("It : 401 상태와 UNAUTHORIZED를 반환한다")
            void it_return_401_unauthorized() throws Exception {
                //when-then
                // JwtAuthenticationFilter는 pass-through라 인증을 세팅하지 않는다.
                // 실제 서비스에서 확인된 것과 동일하게, 미인증 상태로 /api/auth/logout에 접근하면
                // ExceptionTranslationFilter가 JwtAuthenticationEntryPoint를 호출해 401을 반환한다.
                mockMvc.perform(
                                delete("/api/auth/logout")
                                        .with(csrf())
                        )
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().contentType("application/json;charset=UTF-8"))
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andExpect(jsonPath("$.message").value("계정 인증이 필요합니다."))
                        .andExpect(jsonPath("$.data").isEmpty())
                        .andDo(print());
            }
        }
    }
}
