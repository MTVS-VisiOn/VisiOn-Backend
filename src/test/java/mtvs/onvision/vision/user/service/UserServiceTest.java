package mtvs.onvision.vision.user.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.auth.dto.RefreshRequest;
import mtvs.onvision.vision.auth.dto.TokenBody;
import mtvs.onvision.vision.auth.repository.RefreshTokenRepository;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService의")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    Long userId = 1L;
    Long wardId = 2L;
    String email = "user@test.com";
    String password = "password1234";
    String encodedPassword = "encodedPassword1234";
    String nickname = "테스트유저";
    String phoneNumber = "010-1234-5678";

    SignupRequest signupRequest;
    LoginRequest loginRequest;
    RefreshRequest refreshRequest;
    User user;
    User ward;

    @Nested
    @DisplayName("Describe: signup 메서드는")
    class Describe_with_signup {

        @Nested
        @DisplayName("Context: 이미 존재하는 이메일이 주어지면")
        class Context_with_existing_email {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : EXIST_EMAIL 오류 발생")
            void it_throws_exist_email() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(true);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.EXIST_EMAIL.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 이미 존재하는 전화번호가 주어지면")
        class Context_with_existing_phone_number {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : EXIST_PHONENUMBER 오류 발생")
            void it_throws_exist_phone_number() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(true);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.EXIST_PHONENUMBER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: role이 WARD이고 올바른 데이터가 주어지면")
        class Context_with_ward_role {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : User 저장 성공")
            void it_success_signup_without_ward() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(passwordEncoder.encode(password)).willReturn(encodedPassword);
                //when
                userService.signup(signupRequest);

                //then
                ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(captor.capture());
                User saved = captor.getValue();

                assertThat(saved.getEmail()).isEqualTo(email);
                assertThat(saved.getPassword()).isEqualTo(encodedPassword);
                assertThat(saved.getNickname()).isEqualTo(nickname);
                assertThat(saved.getPhoneNumber()).isEqualTo(phoneNumber);
                assertThat(saved.getRole()).isEqualTo(UserRole.WARD);
            }
        }

        @Nested
        @DisplayName("Context: role이 WARD가 아니고 유효한 wardId가 주어지면")
        class Context_with_available_ward_id {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, wardId);
                ward = new User("ward@test.com", "encoded", "보호대상자", "010-0000-0000");
                ReflectionTestUtils.setField(ward, "id", wardId);
            }

            @Test
            @DisplayName("It : User 저장 성공")
            void it_success_signup_with_ward() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(passwordEncoder.encode(password)).willReturn(encodedPassword);
                given(userRepository.findByIdAndRole(wardId, UserRole.WARD)).willReturn(Optional.of(ward));
                //when
                userService.signup(signupRequest);

                //then
                ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(captor.capture());
                User saved = captor.getValue();

                assertThat(saved.getEmail()).isEqualTo(email);
                assertThat(saved.getPassword()).isEqualTo(encodedPassword);
                assertThat(saved.getRole()).isEqualTo(UserRole.GUARDIAN);
                assertThat(saved.getWard()).isEqualTo(ward);
            }
        }

        @Nested
        @DisplayName("Context: role이 WARD가 아니고 유효하지 않은 wardId가 주어지면")
        class Context_with_unavailable_ward_id {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, wardId);
            }

            @Test
            @DisplayName("It : NOT_FOUND_WARD 오류 발생")
            void it_throws_not_found_ward() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(userRepository.findByIdAndRole(wardId, UserRole.WARD)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_WARD.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: login 메서드는")
    class Describe_with_login {

        KeyPair keyPair = new KeyPair("accessToken", "refreshToken");

        @Nested
        @DisplayName("Context: 올바른 이메일과 비밀번호가 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setup() {
                loginRequest = new LoginRequest(email, password);
                user = new User(email, encodedPassword, nickname, phoneNumber);
                ReflectionTestUtils.setField(user, "id", userId);
            }

            @Test
            @DisplayName("It : 로그인 성공 및 KeyPair 반환")
            void it_success_login() {
                //given
                given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);
                given(jwtTokenProvider.issueKeyPair(userId, email, user.getRole())).willReturn(keyPair);
                //when
                KeyPair response = userService.login(loginRequest);

                //then
                assertThat(response).isEqualTo(keyPair);
                verify(refreshTokenRepository).save(userId, keyPair.refreshToken());
            }
        }

        @Nested
        @DisplayName("Context: 존재하지 않는 이메일이 주어지면")
        class Context_with_unavailable_email {
            @BeforeEach
            void setup() {
                loginRequest = new LoginRequest(email, password);
            }

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(userRepository.findByEmail(email)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(loginRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 비밀번호가 일치하지 않으면")
        class Context_with_not_matching_password {
            @BeforeEach
            void setup() {
                loginRequest = new LoginRequest(email, password);
                user = new User(email, encodedPassword, nickname, phoneNumber);
                ReflectionTestUtils.setField(user, "id", userId);
            }

            @Test
            @DisplayName("It : NOT_MATCH_PASSWORD 오류 발생")
            void it_throws_not_match_password() {
                //given
                given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                given(passwordEncoder.matches(password, encodedPassword)).willReturn(false);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(loginRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_MATCH_PASSWORD.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: refreshToken 메서드는")
    class Describe_with_refreshToken {

        String requestToken = "requestRefreshToken";
        TokenBody tokenBody = new TokenBody(userId, email, UserRole.WARD);
        KeyPair keyPair = new KeyPair("newAccessToken", "newRefreshToken");

        @Nested
        @DisplayName("Context: 올바른 refreshToken이 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setup() {
                refreshRequest = new RefreshRequest(requestToken);
            }

            @Test
            @DisplayName("It : 토큰 재발급 성공")
            void it_success_refresh_token() {
                //given
                given(jwtTokenProvider.parseJwt(requestToken)).willReturn(tokenBody);
                given(refreshTokenRepository.getToken(userId)).willReturn(Optional.of(requestToken));
                given(jwtTokenProvider.issueKeyPair(userId, email, UserRole.WARD)).willReturn(keyPair);
                //when
                KeyPair response = userService.refreshToken(refreshRequest);

                //then
                assertThat(response).isEqualTo(keyPair);
                verify(refreshTokenRepository).save(userId, keyPair.refreshToken());
            }
        }

        @Nested
        @DisplayName("Context: 저장소에 refreshToken이 존재하지 않으면")
        class Context_with_no_stored_token {
            @BeforeEach
            void setup() {
                refreshRequest = new RefreshRequest(requestToken);
            }

            @Test
            @DisplayName("It : NOT_FOUND_REFRESH 오류 발생")
            void it_throws_not_found_refresh() {
                //given
                given(jwtTokenProvider.parseJwt(requestToken)).willReturn(tokenBody);
                given(refreshTokenRepository.getToken(userId)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.refreshToken(refreshRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_REFRESH.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 저장된 토큰과 요청 토큰이 일치하지 않으면")
        class Context_with_mismatching_token {
            @BeforeEach
            void setup() {
                refreshRequest = new RefreshRequest(requestToken);
            }

            @Test
            @DisplayName("It : INVALID_REFRESH_TOKEN 오류 발생")
            void it_throws_invalid_refresh_token() {
                //given
                given(jwtTokenProvider.parseJwt(requestToken)).willReturn(tokenBody);
                given(refreshTokenRepository.getToken(userId)).willReturn(Optional.of("differentToken"));
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.refreshToken(refreshRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: loadUserByUsername 메서드는")
    class Describe_with_loadUserByUsername {

        @Nested
        @DisplayName("Context: 존재하는 이메일이라면")
        class Context_with_available_email {
            @BeforeEach
            void setup() {
                user = new User(email, encodedPassword, nickname, phoneNumber);
                ReflectionTestUtils.setField(user, "id", userId);
            }

            @Test
            @DisplayName("It : CurrentUser 조회 성공")
            void it_success_load_user() {
                //given
                given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                //when
                UserDetails response = userService.loadUserByUsername(email);

                //then
                Assertions.assertNotNull(response);
                CurrentUser currentUser = (CurrentUser) response;
                assertThat(currentUser.getId()).isEqualTo(userId);
                assertThat(currentUser.getEmail()).isEqualTo(email);
            }
        }

        @Nested
        @DisplayName("Context: 존재하지 않는 이메일이라면")
        class Context_with_unavailable_email {
            @BeforeEach
            void setup() {
            }

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(userRepository.findByEmail(email)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.loadUserByUsername(email));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: logout 메서드는")
    class Describe_with_logout {

        @Nested
        @DisplayName("Context: 기본적으로")
        class Context_with_available_data {

            CurrentUser currentUser = new CurrentUser(userId, email, UserRole.WARD);

            @Test
            @DisplayName("It : refreshToken 삭제 성공")
            void it_success_logout() {
                //when
                userService.logout(currentUser);

                //then
                verify(refreshTokenRepository).delete(userId);
            }
        }
    }
}
